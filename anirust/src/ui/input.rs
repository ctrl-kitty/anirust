pub(crate) fn input_len(input: &str) -> usize {
    input.chars().count()
}

pub(crate) fn insert_char(input: &mut String, cursor: &mut usize, ch: char) {
    let idx = byte_index(input, *cursor);
    input.insert(idx, ch);
    *cursor = cursor.saturating_add(1);
}

pub(crate) fn delete_char(input: &mut String, cursor: &mut usize) {
    if *cursor == 0 {
        return;
    }

    let idx = byte_index(input, *cursor);
    let prev_idx = prev_byte_index(input, *cursor);
    input.replace_range(prev_idx..idx, "");
    *cursor = cursor.saturating_sub(1);
}

pub(crate) fn move_cursor_left(cursor: &mut usize) {
    if *cursor > 0 {
        *cursor -= 1;
    }
}

pub(crate) fn move_cursor_right(cursor: &mut usize, input: &str) {
    if *cursor < input_len(input) {
        *cursor += 1;
    }
}

pub(crate) fn visible_input(input: &str, cursor: usize, width: u16) -> (String, u16) {
    let width = width as usize;
    if width == 0 {
        return (String::new(), 0);
    }

    let chars: Vec<char> = input.chars().collect();
    let cursor = cursor.min(chars.len());
    let start = if cursor >= width {
        cursor - width + 1
    } else {
        0
    };
    let end = (start + width).min(chars.len());
    let visible: String = chars[start..end].iter().collect();
    let cursor_offset = (cursor - start) as u16;
    (visible, cursor_offset)
}

fn byte_index(input: &str, cursor: usize) -> usize {
    input
        .char_indices()
        .nth(cursor)
        .map(|(idx, _)| idx)
        .unwrap_or_else(|| input.len())
}

fn prev_byte_index(input: &str, cursor: usize) -> usize {
    if cursor == 0 {
        return 0;
    }

    input
        .char_indices()
        .nth(cursor - 1)
        .map(|(idx, _)| idx)
        .unwrap_or(0)
}
