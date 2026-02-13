use super::input::InputState;

#[test]
fn input_state_inserts_deletes_and_moves() {
    let mut input = InputState::new();
    input.insert_char('a');
    input.insert_char('b');
    input.insert_char('c');

    assert_eq!(input.value(), "abc");
    assert_eq!(input.cursor(), 3);

    input.move_left();
    input.delete_char();
    assert_eq!(input.value(), "ac");
    assert_eq!(input.cursor(), 1);

    input.move_home();
    input.insert_char('z');
    assert_eq!(input.value(), "zac");
    assert_eq!(input.cursor(), 1);

    input.move_end();
    let cursor = input.cursor();
    input.move_right();
    assert_eq!(input.cursor(), cursor);
}

#[test]
fn input_state_visible_window_tracks_cursor() {
    let mut input = InputState::new();
    for ch in "abcdef".chars() {
        input.insert_char(ch);
    }

    input.move_left();
    let (visible, offset) = input.visible(4);
    assert_eq!(visible, "cdef");
    assert_eq!(offset, 3);
}

#[test]
fn input_state_clear_resets_value_and_cursor() {
    let mut input = InputState::new();
    input.insert_char('x');
    input.move_left();
    input.clear();
    assert!(input.is_empty());
    assert_eq!(input.cursor(), 0);
}
