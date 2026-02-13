use crate::domain::AnimeId;

#[test]
fn anime_id_empty_by_default() {
    let id = AnimeId::default();
    assert!(id.is_empty());
}
