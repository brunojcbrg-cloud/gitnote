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
