use std::{collections::HashMap, time::Instant};

use super::*;

struct RecordingProgress {
    events: std::sync::Arc<std::sync::Mutex<Vec<(i32, i32)>>>,
}

impl crate::callback::ProgressCB for RecordingProgress {
    fn progress(&mut self, current: i32, total: i32) -> bool {
        self.events.lock().unwrap().push((current, total));
        true
    }
}

fn commit_file(repo: &git2::Repository, path: &str, content: &str, message: &str) {
    std::fs::write(repo.workdir().unwrap().join(path), content).unwrap();
    let mut index = repo.index().unwrap();
    index.add_path(std::path::Path::new(path)).unwrap();
    index.write().unwrap();
    let tree_id = index.write_tree().unwrap();
    let tree = repo.find_tree(tree_id).unwrap();
    let signature = git2::Signature::now("Teste", "teste@example.com").unwrap();
    let parent = repo.head().ok().and_then(|head| head.peel_to_commit().ok());
    let parents: Vec<&git2::Commit> = parent.iter().collect();
    repo.commit(Some("HEAD"), &signature, &signature, message, &tree, &parents)
        .unwrap();
}

fn commit_all(repo: &git2::Repository, message: &str) -> git2::Oid {
    let mut index = repo.index().unwrap();
    index.add_all(["*"], git2::IndexAddOption::DEFAULT, None).unwrap();
    index.write().unwrap();
    let tree_id = index.write_tree().unwrap();
    let tree = repo.find_tree(tree_id).unwrap();
    let signature = git2::Signature::now("Teste", "teste@example.com").unwrap();
    let parent = repo.head().ok().and_then(|head| head.peel_to_commit().ok());
    let parents: Vec<&git2::Commit> = parent.iter().collect();
    repo.commit(Some("HEAD"), &signature, &signature, message, &tree, &parents).unwrap()
}

fn tracking_repo(label: &str) -> (std::path::PathBuf, git2::Repository) {
    let root = std::env::temp_dir().join(format!(
        "gitnote-dashboard-{label}-{}",
        std::process::id()
    ));
    let _ = std::fs::remove_dir_all(&root);
    let repo = git2::Repository::init(&root).unwrap();
    std::fs::write(root.join("base.md"), "base").unwrap();
    let base = commit_all(&repo, "base");
    repo.reference("refs/remotes/origin/master", base, true, "test").unwrap();
    (root, repo)
}

#[test]
fn ahead_behind_covers_equal_ahead_behind_and_diverged() {
    let (root, repo) = tracking_repo("graph");
    assert_eq!(ahead_behind_for_repo(&repo).unwrap(), (0, 0));

    std::fs::write(root.join("ahead.md"), "ahead").unwrap();
    commit_all(&repo, "ahead");
    assert_eq!(ahead_behind_for_repo(&repo).unwrap(), (1, 0));

    let base = repo.refname_to_id("refs/remotes/origin/master").unwrap();
    repo.set_head_detached(base).unwrap();
    let remote_one = {
        std::fs::write(root.join("remote-1.md"), "one").unwrap();
        commit_all(&repo, "remote one")
    };
    let remote_two = {
        std::fs::write(root.join("remote-2.md"), "two").unwrap();
        commit_all(&repo, "remote two")
    };
    repo.reference("refs/remotes/origin/master", remote_two, true, "test").unwrap();
    repo.set_head("refs/heads/master").unwrap();
    assert_eq!(ahead_behind_for_repo(&repo).unwrap(), (1, 2));

    repo.reference("refs/heads/master", base, true, "test").unwrap();
    assert_eq!(ahead_behind_for_repo(&repo).unwrap(), (0, 2));
    assert_ne!(remote_one, base);
    drop(repo);
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn status_reports_clean_modified_new_and_deleted() {
    let (root, repo) = tracking_repo("status");
    assert!(status_for_repo(&repo).unwrap().is_empty());
    std::fs::write(root.join("base.md"), "changed").unwrap();
    std::fs::write(root.join("new.md"), "new").unwrap();
    std::fs::write(root.join("ignored.md"), "ignored").unwrap();
    std::fs::write(root.join(".gitignore"), "ignored.md\n").unwrap();
    std::fs::remove_file(root.join("base.md")).unwrap();
    let changes = status_for_repo(&repo).unwrap();
    assert!(changes.iter().any(|it| it.path == "base.md" && it.kind == "deleted"));
    assert!(changes.iter().any(|it| it.path == "new.md" && it.kind == "new"));
    assert!(!changes.iter().any(|it| it.path == "ignored.md"));

    std::fs::write(root.join("base.md"), "modified").unwrap();
    let changes = status_for_repo(&repo).unwrap();
    assert!(changes.iter().any(|it| it.path == "base.md" && it.kind == "modified"));
    drop(repo);
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn recent_commits_respects_limit_and_lists_touched_files() {
    let (root, repo) = tracking_repo("history");
    std::fs::write(root.join("second.md"), "second").unwrap();
    let second = commit_all(&repo, "second");
    repo.reference("refs/remotes/origin/master", second, true, "test").unwrap();
    let commits = recent_commits_for_repo(&repo, 1).unwrap();
    assert_eq!(commits.len(), 1);
    assert_eq!(commits[0].message, "second");
    assert_eq!(commits[0].files, vec!["second.md"]);
    drop(repo);
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn pull_reports_transfer_progress_through_completion() {
    let root = std::env::temp_dir().join(format!("gitnote-pull-progress-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&root);
    let remote_path = root.join("remote.git");
    let source_path = root.join("source");
    let clone_path = root.join("clone");

    git2::Repository::init_bare(&remote_path).unwrap();
    let source = git2::Repository::init(&source_path).unwrap();
    commit_file(&source, "primeiro.md", "um", "primeiro");
    let mut remote = source.remote("origin", remote_path.to_str().unwrap()).unwrap();
    remote.push(&["refs/heads/master:refs/heads/master"], None).unwrap();

    clone_repo(
        clone_path.to_str().unwrap(),
        remote_path.to_str().unwrap(),
        None,
        crate::callback::DummyProgressCB,
    )
    .unwrap();

    commit_file(&source, "segundo.md", "dois", "segundo");
    remote.push(&["refs/heads/master:refs/heads/master"], None).unwrap();

    let events = std::sync::Arc::new(std::sync::Mutex::new(Vec::new()));
    pull(
        None,
        &GitAuthor {
            name: "Teste".into(),
            email: "teste@example.com".into(),
        },
        RecordingProgress {
            events: events.clone(),
        },
    )
    .unwrap();

    let recorded = events.lock().unwrap();
    assert!(recorded.iter().any(|(current, total)| *total > 0 && current == total));
    assert!(recorded.contains(&(-1, -1)));
    drop(recorded);
    close();
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
#[ignore = "local repo"]
fn timestamp() {
    open_repo("../../../../../repo_test").unwrap();

    let mut timestamps = HashMap::new();

    let now = Instant::now();

    get_timestamps(|path, time| {
        timestamps.insert(path.to_string(), time);
        Ok(())
    })
    .unwrap();

    let elapsed = now.elapsed();

    println!("{elapsed:?}");
}

// cargo test timestamp2 --release -- --nocapture --ignored
#[test]
#[ignore = "local repo"]
fn timestamp2() {
    open_repo("../../../../../note-pv").unwrap();

    let mut timestamps = HashMap::new();
    let now = Instant::now();

    get_timestamps(|path, time| {
        timestamps.entry(path.to_string()).or_insert(time);

        Ok(())
    })
    .unwrap();

    let mut res = timestamps.into_iter().collect::<Vec<_>>();

    res.sort_by(|a, b| a.1.cmp(&b.1));

    dbg!(&res);

    let elapsed = now.elapsed();

    println!("{elapsed:?}");
}
