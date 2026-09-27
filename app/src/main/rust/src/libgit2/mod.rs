use std::{
    fs,
    path::Path,
    str::FromStr,
    sync::{LazyLock, Mutex, OnceLock},
};

use git2::{
    CertificateCheckStatus, FetchOptions, IndexAddOption, Progress, PushOptions, RemoteCallbacks,
    Repository, Signature, Status, StatusOptions,
};

use crate::{Cred, Error, GitAuthor, callback::ProgressCB, mime_types::is_extension_supported};

mod merge;
#[cfg(test)]
mod test;
#[cfg(test)]
mod test_clone;

#[cfg(test)]
mod test_merge;

const REMOTE: &str = "origin";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WorkingTreeChange {
    pub path: String,
    pub kind: &'static str,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RecentCommit {
    pub short_hash: String,
    pub author: String,
    pub timestamp: i64,
    pub message: String,
    pub files: Vec<String>,
}

static REPO: LazyLock<Mutex<Option<Repository>>> = LazyLock::new(|| Mutex::new(None));

// https://github.com/libgit2/libgit2/pull/7056
static HOME_PATH: OnceLock<String> = OnceLock::new();

fn apply_ssh_workaround(clone: bool) {
    let Some(home) = HOME_PATH.get() else {
        warn!("home path not set");
        return;
    };

    if clone {
        unsafe {
            std::env::set_var("HOME", home);
        }
    } else {
        let c_path = std::ffi::CString::from_str(home).expect("CString::new failed");

        unsafe {
            libgit2_sys::git_libgit2_opts(
                libgit2_sys::GIT_OPT_SET_HOMEDIR as std::ffi::c_int,
                c_path.as_ptr(),
            )
        };
    }

    if let Err(e) = std::fs::create_dir_all(format!("{home}/.ssh")) {
        error!("{e}");
    }
    if let Err(e) = std::fs::File::create(format!("{home}/.ssh/known_hosts")) {
        error!("{e}");
    }
}

pub fn init_lib(home_path: String) {
    info!("home_path: {home_path}");
    let _ = HOME_PATH.set(home_path.clone());

    git2::trace_set(git2::TraceLevel::Warn, |level, msg| {
        let msg = String::from_utf8_lossy(msg);

        match level {
            git2::TraceLevel::None => debug!("{msg}"),
            git2::TraceLevel::Fatal => error!("{msg}"),
            git2::TraceLevel::Error => error!("{msg}"),
            git2::TraceLevel::Warn => warn!("{msg}"),
            git2::TraceLevel::Info => info!("{msg}"),
            git2::TraceLevel::Debug => debug!("{msg}"),
            git2::TraceLevel::Trace => trace!("{msg}"),
        }
    })
    .unwrap();

    unsafe {
        std::env::set_var("HOME", &home_path);
    }

    let git_config_path = Path::new(&home_path).join(".gitconfig");

    let git_config_content = "[safe]\n\tdirectory = *";

    match fs::exists(&git_config_path) {
        Ok(true) => {}
        Ok(false) => {
            if let Err(e) = fs::create_dir_all(git_config_path.parent().unwrap()) {
                error!("gitconfig: {e}");
            }

            if let Err(e) = fs::write(&git_config_path, git_config_content) {
                error!("gitconfig: {e}");
            } else {
                debug!("successfully written the gitconfig file")
            }
        }
        Err(e) => {
            error!("gitconfig: {e}");
        }
    }

    unsafe {
        if let Err(e) = git2::opts::set_server_connect_timeout_in_milliseconds(7000) {
            error!("set_server_connect_timeout_in_milliseconds: {e}");
        }

        if let Err(e) = git2::opts::set_server_timeout_in_milliseconds(7000) {
            error!("set_server_timeout_in_milliseconds: {e}");
        }
    };
}

pub fn create_repo(repo_path: &str) -> Result<(), Error> {
    let repo = Repository::init(repo_path).map_err(|e| Error::git2(e, "Repository::init"))?;

    REPO.lock().unwrap().replace(repo);

    Ok(())
}

pub fn open_repo(repo_path: &str) -> Result<(), Error> {
    let repo = Repository::open(repo_path).map_err(|e| Error::git2(e, "Repository::open"))?;

    REPO.lock().unwrap().replace(repo);

    Ok(())
}

fn current_branch(repo: &Repository) -> Result<String, Error> {
    let head = repo.head().map_err(|e| Error::git2(e, "head"))?;

    if head.is_branch()
        && let Ok(name) = head.shorthand()
    {
        return Ok(name.to_string());
    }

    // Detached HEAD or not a branch
    Err(Error::git2(
        git2::Error::from_str("unable to determine default branch"),
        "",
    ))
}

fn credential_helper(
    cred: &Cred,
    username_from_url: Option<&str>,
) -> Result<git2::Cred, git2::Error> {
    match cred {
        Cred::UserPassPlainText { username, password } => {
            git2::Cred::userpass_plaintext(username, password)
        }
        Cred::Ssh {
            private_key,
            public_key,
            passphrase,
        } => git2::Cred::ssh_key_from_memory(
            username_from_url.unwrap_or("git"),
            Some(public_key),
            private_key,
            passphrase.as_deref(),
        ),
    }
}

pub fn clone_repo(
    repo_path: &str,
    remote_url: &str,
    cred: Option<Cred>,
    mut cb: impl ProgressCB,
) -> Result<(), Error> {
    apply_ssh_workaround(true);
    let mut callbacks = RemoteCallbacks::new();

    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));

    if let Some(cred) = cred {
        callbacks.credentials(move |_url, username_from_url, _allowed_types| {
            debug!("allowed_types: {:?}", _allowed_types);
            credential_helper(&cred, username_from_url)
        });
    }

    callbacks.transfer_progress(|stats: Progress| {
        cb.progress(stats.indexed_objects() as i32, stats.total_objects() as i32)
    });

    let mut fetch_options = FetchOptions::new();
    fetch_options
        .remote_callbacks(callbacks)
        .download_tags(git2::AutotagOption::None);

    let mut builder = git2::build::RepoBuilder::new();

    let repo = builder
        .fetch_options(fetch_options)
        .clone(remote_url, std::path::Path::new(&repo_path))
        .map_err(|e| Error::git2(e, "clone"))?;

    REPO.lock().unwrap().replace(repo);

    Ok(())
}

pub fn last_commit() -> Option<String> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // new repo have no commit, so this function can fail
    let head = repo.refname_to_id("HEAD").ok()?;

    Some(head.to_string())
}

pub fn signature() -> Option<(String, String)> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref()?;

    if let Ok(signature) = repo.signature() {
        let name = signature.name().unwrap_or_default().to_string();
        let email = signature.email().unwrap_or_default().to_string();

        if !name.is_empty() || !email.is_empty() {
            return Some((name, email));
        }
    }

    let head = repo.head().ok()?;
    let commit = head.peel_to_commit().ok()?;
    let author = commit.author();

    Some((
        author.name().unwrap_or_default().to_string(),
        author.email().unwrap_or_default().to_string(),
    ))
}

pub fn commit_all(name: &str, email: &str, message: &str) -> Result<(), Error> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    let mut index = repo.index().map_err(|e| Error::git2(e, "index"))?;

    index
        .add_all(["*"].iter(), IndexAddOption::DEFAULT, None)
        .map_err(|e| Error::git2(e, "add_all"))?;

    // Write index to disk
    index.write().map_err(|e| Error::git2(e, "write"))?;

    // Write tree
    let tree_oid = index
        .write_tree()
        .map_err(|e| Error::git2(e, "write_tree"))?;

    let tree = repo
        .find_tree(tree_oid)
        .map_err(|e| Error::git2(e, "find_tree"))?;

    // Get HEAD commit as parent, and Allow initial commit
    let parent_commit = repo.head().and_then(|r| r.peel_to_commit()).ok();

    let sig = Signature::now(name, email).map_err(|e| Error::git2(e, "Signature::now"))?;

    // Create commit
    match parent_commit {
        Some(ref parent) => repo.commit(Some("HEAD"), &sig, &sig, message, &tree, &[parent]),
        None => repo.commit(Some("HEAD"), &sig, &sig, message, &tree, &[]),
    }
    .map(|_| ())
    .map_err(|e| Error::git2(e, "commit"))
}

pub fn push(cred: Option<Cred>, mut cb: impl ProgressCB) -> Result<(), Error> {
    apply_ssh_workaround(false);

    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    let mut remote = repo
        .find_remote(REMOTE)
        .map_err(|e| Error::git2(e, "find_remote"))?;

    let branch = current_branch(repo)?;
    let refspecs = [format!("refs/heads/{branch}:refs/heads/{branch}")];

    let mut callbacks = RemoteCallbacks::new();

    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));

    if let Some(cred) = cred {
        callbacks.credentials(move |_url, username_from_url, _allowed_types| {
            credential_helper(&cred, username_from_url)
        });
    }
    callbacks.push_transfer_progress(|current, total, _bytes| {
        cb.progress(current as i32, total as i32);
    });

    let mut push_opts = PushOptions::new();
    push_opts.remote_callbacks(callbacks);

    remote
        .push(&refspecs, Some(&mut push_opts))
        .map_err(|e| Error::git2(e, "push"))?;

    Ok(())
}

fn fetch_remote(repo: &Repository, cred: Option<Cred>) -> Result<(), Error> {
    apply_ssh_workaround(false);
    let mut remote = repo
        .find_remote(REMOTE)
        .map_err(|e| Error::git2(e, "find_remote"))?;
    let mut callbacks = RemoteCallbacks::new();
    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));
    if let Some(cred) = cred {
        callbacks.credentials(move |_url, username_from_url, _allowed_types| {
            credential_helper(&cred, username_from_url)
        });
    }
    let branch = current_branch(repo)?;
    let refspec = format!("+refs/heads/{branch}:refs/remotes/origin/{branch}");
    let mut options = FetchOptions::new();
    options
        .remote_callbacks(callbacks)
        .download_tags(git2::AutotagOption::None);
    remote
        .fetch(&[&refspec], Some(&mut options), None)
        .map_err(|e| Error::git2(e, "fetch"))
}

/// Updates only the remote-tracking reference. It never merges or checks out files.
pub fn fetch(cred: Option<Cred>) -> Result<(), Error> {
    let repo = REPO.lock().expect("repo lock");
    fetch_remote(repo.as_ref().expect("repo"), cred)
}

fn ahead_behind_for_repo(repo: &Repository) -> Result<(usize, usize), Error> {
    let branch = current_branch(repo)?;
    let local = repo.refname_to_id("HEAD")?;
    let remote = repo.refname_to_id(&format!("refs/remotes/{REMOTE}/{branch}"))?;
    repo.graph_ahead_behind(local, remote).map_err(Error::from)
}

pub fn ahead_behind() -> Result<(usize, usize), Error> {
    let repo = REPO.lock().expect("repo lock");
    ahead_behind_for_repo(repo.as_ref().expect("repo"))
}

fn status_for_repo(repo: &Repository) -> Result<Vec<WorkingTreeChange>, Error> {
    let mut options = StatusOptions::new();
    options
        .include_untracked(true)
        .recurse_untracked_dirs(true)
        .include_ignored(false);
    let statuses = repo.statuses(Some(&mut options))?;
    Ok(statuses
        .iter()
        .filter_map(|entry| {
            let flags = entry.status();
            let kind = if flags.intersects(Status::WT_DELETED | Status::INDEX_DELETED) {
                "deleted"
            } else if flags.intersects(Status::WT_NEW | Status::INDEX_NEW) {
                "new"
            } else if flags.intersects(
                Status::WT_MODIFIED
                    | Status::INDEX_MODIFIED
                    | Status::WT_RENAMED
                    | Status::INDEX_RENAMED
                    | Status::WT_TYPECHANGE
                    | Status::INDEX_TYPECHANGE,
            ) {
                "modified"
            } else {
                return None;
            };
            match entry.path() {
                Ok(path) => Some(WorkingTreeChange {
                    path: path.to_string(),
                    kind,
                }),
                Err(e) => {
                    warn!("status entry path is not valid utf-8: {e}");
                    None
                }
            }
        })
        .collect())
}

pub fn status() -> Result<Vec<WorkingTreeChange>, Error> {
    let repo = REPO.lock().expect("repo lock");
    status_for_repo(repo.as_ref().expect("repo"))
}

fn recent_commits_for_repo(repo: &Repository, n: usize) -> Result<Vec<RecentCommit>, Error> {
    let branch = current_branch(repo)?;
    let start = repo
        .refname_to_id(&format!("refs/remotes/{REMOTE}/{branch}"))
        .or_else(|_| repo.refname_to_id("HEAD"))?;
    let mut walk = repo.revwalk()?;
    walk.push(start)?;
    walk.set_sorting(git2::Sort::TIME)?;
    let mut result = Vec::new();
    for oid in walk.take(n) {
        let commit = repo.find_commit(oid?)?;
        let tree = commit.tree()?;
        let parent_tree = if commit.parent_count() > 0 {
            Some(commit.parent(0)?.tree()?)
        } else {
            None
        };
        let diff = repo.diff_tree_to_tree(parent_tree.as_ref(), Some(&tree), None)?;
        let mut files = diff
            .deltas()
            .filter_map(|delta| delta.new_file().path().or_else(|| delta.old_file().path()))
            .filter_map(|path| path.to_str().map(str::to_string))
            .collect::<Vec<_>>();
        files.sort();
        files.dedup();
        result.push(RecentCommit {
            short_hash: commit.id().to_string()[..7].to_string(),
            author: commit.author().name().unwrap_or("Unknown").to_string(),
            timestamp: commit.time().seconds(),
            message: commit.summary().ok().flatten().unwrap_or("").to_string(),
            files,
        });
    }
    Ok(result)
}

pub fn recent_commits(n: usize) -> Result<Vec<RecentCommit>, Error> {
    let repo = REPO.lock().expect("repo lock");
    recent_commits_for_repo(repo.as_ref().expect("repo"), n)
}

pub fn pull(
    cred: Option<Cred>,
    author: &GitAuthor,
    mut cb: impl ProgressCB,
) -> Result<(), Error> {
    apply_ssh_workaround(false);

    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    let mut remote = repo
        .find_remote(REMOTE)
        .map_err(|e| Error::git2(e, "find_remote"))?;

    let mut callbacks = RemoteCallbacks::new();

    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));

    if let Some(cred) = cred {
        callbacks.credentials(move |_url, username_from_url, _allowed_types| {
            credential_helper(&cred, username_from_url)
        });
    }
    callbacks.transfer_progress(|stats: Progress| {
        cb.progress(stats.received_objects() as i32, stats.total_objects() as i32)
    });

    let mut fetch_options = FetchOptions::new();
    fetch_options
        .remote_callbacks(callbacks)
        .download_tags(git2::AutotagOption::None);

    let branch = current_branch(repo)?;
    let refspec = format!("+refs/heads/{}:refs/remotes/origin/{}", branch, branch);
    remote
        .fetch(&[&refspec], Some(&mut fetch_options), None)
        .map_err(|e| Error::git2(e, "fetch"))?;

    drop(fetch_options);
    cb.progress(-1, -1);

    let fetch_head = repo
        .find_reference("FETCH_HEAD")
        .map_err(|e| Error::git2(e, "find_reference"))?;

    let commit = repo
        .reference_to_annotated_commit(&fetch_head)
        .map_err(|e| Error::git2(e, "reference_to_annotated_commit"))?;

    merge::do_merge(repo, &branch, commit, author).map_err(|e| e.add_message("do_merge"))?;

    Ok(())
}

pub fn close() {
    let mut repo = REPO.lock().expect("repo lock");
    repo.take();
}

pub fn is_change() -> Result<bool, Error> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    let mut opts = StatusOptions::new();
    opts.include_untracked(true).recurse_untracked_dirs(true);

    let statuses = repo
        .statuses(Some(&mut opts))
        .map_err(|e| Error::git2(e, "statuses"))?;

    let count = statuses.len();

    Ok(count > 0)
}

pub fn get_timestamps(
    mut insert: impl FnMut(&str, i64) -> Result<(), jni::errors::Error>,
) -> Result<(), Error> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    let mut revwalk = repo.revwalk()?;
    revwalk.push_head()?;
    revwalk.set_sorting(git2::Sort::TIME)?;

    for oid in revwalk {
        let oid = oid?;
        let commit = repo.find_commit(oid)?;

        let current_tree = commit.tree()?;

        let parent_tree = if commit.parent_count() > 0 {
            Some(commit.parent(0)?.tree()?)
        } else {
            None
        };

        let mut opts = git2::DiffOptions::new();

        let diff =
            repo.diff_tree_to_tree(parent_tree.as_ref(), Some(&current_tree), Some(&mut opts))?;

        for delta in diff.deltas() {
            let path = delta.new_file().path().or_else(|| delta.old_file().path());

            if let Some(path) = path
                && is_extension_supported(
                    Path::new(&path)
                        .extension()
                        .and_then(|e| e.to_str())
                        .unwrap_or(""),
                ) {
                    match path.as_os_str().to_str() {
                        Some(path) => insert(path, commit.time().seconds() * 1000)?,
                        None => {
                            warn!("path can't be converted to str");
                        }
                    }
                }
        }
    }

    Ok(())
}
