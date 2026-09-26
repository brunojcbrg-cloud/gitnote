use git2::{Repository, Signature};

use crate::{Error, GitAuthor};

fn ensure_clean_workdir(repo: &Repository) -> Result<(), git2::Error> {
    let mut options = git2::StatusOptions::new();
    options
        .include_untracked(true)
        .recurse_untracked_dirs(true)
        .exclude_submodules(true);
    if !repo.statuses(Some(&mut options))?.is_empty() {
        return Err(git2::Error::from_str(
            "working tree has uncommitted changes; refusing merge",
        ));
    }
    Ok(())
}

fn fast_forward(
    repo: &Repository,
    lb: &mut git2::Reference,
    rc: &git2::AnnotatedCommit,
) -> Result<(), git2::Error> {
    let name = match lb.name() {
        Ok(s) => s.to_string(),
        Err(_) => String::from_utf8_lossy(lb.name_bytes()).to_string(),
    };
    let msg = format!("Fast-Forward: Setting {} to id: {}", name, rc.id());
    lb.set_target(rc.id(), &msg)?;
    repo.set_head(&name)?;
    repo.checkout_head(Some(
        git2::build::CheckoutBuilder::default()
            // For some reason the force is required to make the working directory actually get updated
            // I suspect we should be adding some logic to handle dirty working directory states
            // but this is just an example so maybe not.
            .force(),
    ))?;
    Ok(())
}

fn normal_merge(
    repo: &Repository,
    local: &git2::AnnotatedCommit,
    remote: &git2::AnnotatedCommit,
    author: &GitAuthor,
) -> Result<(), git2::Error> {
    let local_tree = repo.find_commit(local.id())?.tree()?;
    let remote_tree = repo.find_commit(remote.id())?.tree()?;
    let ancestor = repo
        .find_commit(repo.merge_base(local.id(), remote.id())?)?
        .tree()?;
    let mut idx = repo.merge_trees(&ancestor, &local_tree, &remote_tree, None)?;

    if idx.has_conflicts() {
        let mut paths = Vec::new();
        for conflict in idx.conflicts()? {
            let conflict = conflict?;
            let path = conflict
                .our
                .as_ref()
                .or(conflict.their.as_ref())
                .or(conflict.ancestor.as_ref())
                .map(|entry| String::from_utf8_lossy(&entry.path).to_string())
                .unwrap_or_else(|| "arquivo desconhecido".to_string());
            if !paths.contains(&path) {
                paths.push(path);
            }
        }
        let message = format!("merge conflict in {}", paths.join(", "));
        error!("{message}");
        // `merge_trees` trabalha apenas em memória. Não faça checkout do índice
        // conflitado: isso escreveria marcadores na nota e transformaria conflito
        // em falso sucesso. Retornar aqui mantém HEAD e working tree intactos.
        return Err(git2::Error::from_str(&message));
    }
    let result_tree = repo.find_tree(idx.write_tree_to(repo)?)?;
    // now create the merge commit
    let msg = format!("Merge: {} into {}", remote.id(), local.id());
    let sig = Signature::now(&author.name, &author.email)?;

    let local_commit = repo.find_commit(local.id())?;
    let remote_commit = repo.find_commit(remote.id())?;
    // Do our merge commit and set current branch head to that commit.
    let _merge_commit = repo.commit(
        Some("HEAD"),
        &sig,
        &sig,
        &msg,
        &result_tree,
        &[&local_commit, &remote_commit],
    )?;
    // Set working tree to match head.
    let mut checkout_opts = git2::build::CheckoutBuilder::new();
    checkout_opts.force();
    repo.checkout_head(Some(&mut checkout_opts))?;

    Ok(())
}

pub fn do_merge<'a>(
    repo: &'a Repository,
    remote_branch: &str,
    fetch_commit: git2::AnnotatedCommit<'a>,
    author: &GitAuthor,
) -> Result<(), Error> {
    ensure_clean_workdir(repo).map_err(|e| Error::git2(e, "pre_merge_workdir"))?;
    // 1. do a merge analysis
    let analysis = repo
        .merge_analysis(&[&fetch_commit])
        .map_err(|e| Error::git2(e, "merge_analysis"))?;

    // 2. Do the appropriate merge
    if analysis.0.is_fast_forward() {
        // do a fast forward
        let refname = format!("refs/heads/{remote_branch}");
        match repo.find_reference(&refname) {
            Ok(mut r) => {
                fast_forward(repo, &mut r, &fetch_commit)?;
            }
            Err(_) => {
                // The branch doesn't exist so just set the reference to the
                // commit directly. Usually this is because you are pulling
                // into an empty repository.
                repo.reference(
                    &refname,
                    fetch_commit.id(),
                    true,
                    &format!("Setting {} to {}", remote_branch, fetch_commit.id()),
                )
                .map_err(|e| Error::git2(e, "reference"))?;
                repo.set_head(&refname)
                    .map_err(|e| Error::git2(e, "set_head"))?;
                repo.checkout_head(Some(
                    git2::build::CheckoutBuilder::default()
                        .allow_conflicts(true)
                        .conflict_style_merge(true)
                        .force(),
                ))
                .map_err(|e| Error::git2(e, "checkout_head"))?;
            }
        };
    } else if analysis.0.is_normal() {
        // do a normal merge
        let head_commit = repo
            .reference_to_annotated_commit(&repo.head()?)
            .map_err(|e| Error::git2(e, "reference_to_annotated_commit"))?;
        normal_merge(repo, &head_commit, &fetch_commit, author)
            .map_err(|e| Error::git2(e, "normal_merge"))?;
    } else {
        // Nothing to do...
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::path::PathBuf;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temp_repo() -> (PathBuf, Repository) {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let path = std::env::temp_dir().join(format!(
            "gitnote-merge-{}-{unique}",
            std::process::id()
        ));
        let repo = Repository::init(&path).unwrap();
        (path, repo)
    }

    fn commit_note(
        repo: &Repository,
        update_ref: Option<&str>,
        parent: Option<git2::Oid>,
        content: &str,
        message: &str,
    ) -> git2::Oid {
        let blob = repo.blob(content.as_bytes()).unwrap();
        let mut builder = repo.treebuilder(None).unwrap();
        builder.insert("Nota.md", blob, 0o100644).unwrap();
        let tree_id = builder.write().unwrap();
        let tree = repo.find_tree(tree_id).unwrap();
        let sig = Signature::now("Teste", "teste@example.com").unwrap();
        let parent_commit = parent.map(|id| repo.find_commit(id).unwrap());
        let parents: Vec<&git2::Commit<'_>> = parent_commit.iter().collect();
        repo.commit(update_ref, &sig, &sig, message, &tree, &parents)
            .unwrap()
    }

    #[test]
    fn conflict_returns_error_without_touching_note_or_head() {
        let (path, repo) = temp_repo();
        let base = commit_note(&repo, Some("refs/heads/master"), None, "base\n", "base");
        let local = commit_note(
            &repo,
            Some("refs/heads/master"),
            Some(base),
            "local\n",
            "local",
        );
        let remote = commit_note(&repo, None, Some(base), "remoto\n", "remoto");
        repo.set_head("refs/heads/master").unwrap();
        repo.checkout_head(Some(git2::build::CheckoutBuilder::new().force()))
            .unwrap();
        let before = fs::read(path.join("Nota.md")).unwrap();
        let local_annotated = repo.find_annotated_commit(local).unwrap();
        let remote_annotated = repo.find_annotated_commit(remote).unwrap();

        let result = normal_merge(
            &repo,
            &local_annotated,
            &remote_annotated,
            &GitAuthor {
                name: "Teste".into(),
                email: "teste@example.com".into(),
            },
        );

        assert!(result.is_err());
        assert!(result.unwrap_err().message().contains("Nota.md"));
        assert_eq!(repo.head().unwrap().target(), Some(local));
        assert_eq!(fs::read(path.join("Nota.md")).unwrap(), before);
        assert_eq!(repo.state(), git2::RepositoryState::Clean);
        drop(repo);
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn dirty_workdir_is_refused_before_force_checkout() {
        let (path, repo) = temp_repo();
        let base = commit_note(&repo, Some("refs/heads/master"), None, "base\n", "base");
        let remote = commit_note(&repo, None, Some(base), "remoto\n", "remoto");
        repo.set_head("refs/heads/master").unwrap();
        repo.checkout_head(Some(git2::build::CheckoutBuilder::new().force()))
            .unwrap();
        fs::write(path.join("Nota.md"), "rascunho local\n").unwrap();
        let remote_annotated = repo.find_annotated_commit(remote).unwrap();

        let result = do_merge(
            &repo,
            "master",
            remote_annotated,
            &GitAuthor {
                name: "Teste".into(),
                email: "teste@example.com".into(),
            },
        );

        assert!(result.is_err());
        assert_eq!(repo.head().unwrap().target(), Some(base));
        assert_eq!(fs::read_to_string(path.join("Nota.md")).unwrap(), "rascunho local\n");
        drop(repo);
        fs::remove_dir_all(path).unwrap();
    }
}
