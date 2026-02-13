#[derive(Debug, Clone, Default)]
pub(crate) struct InputState {
    value: String,
    cursor: usize,
}

impl InputState {
    pub(crate) fn new() -> Self {
        Self::default()
    }

    pub(crate) fn value(&self) -> &str {
        &self.value
    }

    #[cfg(test)]
    pub(crate) fn cursor(&self) -> usize {
        self.cursor
    }

    pub(crate) fn is_empty(&self) -> bool {
        self.value.is_empty()
    }

    pub(crate) fn clear(&mut self) {
        self.value.clear();
        self.cursor = 0;
    }

    pub(crate) fn insert_char(&mut self, ch: char) {
        insert_char(&mut self.value, &mut self.cursor, ch);
    }

    pub(crate) fn delete_char(&mut self) {
        delete_char(&mut self.value, &mut self.cursor);
    }

    pub(crate) fn move_left(&mut self) {
        move_cursor_left(&mut self.cursor);
    }

    pub(crate) fn move_right(&mut self) {
        move_cursor_right(&mut self.cursor, &self.value);
    }

    pub(crate) fn move_home(&mut self) {
        self.cursor = 0;
    }

    pub(crate) fn move_end(&mut self) {
        self.cursor = input_len(&self.value);
    }

    pub(crate) fn visible(&self, width: u16) -> (String, u16) {
        visible_input(&self.value, self.cursor, width)
    }
}

fn input_len(input: &str) -> usize {
    input.chars().count()
}

fn insert_char(input: &mut String, cursor: &mut usize, ch: char) {
    let idx = byte_index(input, *cursor);
    input.insert(idx, ch);
    *cursor = cursor.saturating_add(1);
}

fn delete_char(input: &mut String, cursor: &mut usize) {
    if *cursor == 0 {
        return;
    }

    let idx = byte_index(input, *cursor);
    let prev_idx = prev_byte_index(input, *cursor);
    input.replace_range(prev_idx..idx, "");
    *cursor = cursor.saturating_sub(1);
}

fn move_cursor_left(cursor: &mut usize) {
    if *cursor > 0 {
        *cursor -= 1;
    }
}

fn move_cursor_right(cursor: &mut usize, input: &str) {
    if *cursor < input_len(input) {
        *cursor += 1;
    }
}

fn visible_input(input: &str, cursor: usize, width: u16) -> (String, u16) {
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
