//! Semantic chunker.
//!
//! Splits long text into ~500-1000 token chunks while respecting paragraph
//! and sentence boundaries. Optional overlap to avoid losing context at
//! chunk edges.
//!
//! KISS: no neural sentence segmentation here — Unicode word/sentence
//! segmentation is good enough for the MVP. Upgrade later if recall@K drops.

use serde::{Deserialize, Serialize};
use unicode_segmentation::UnicodeSegmentation;
use uuid::Uuid;

#[derive(Debug, Clone, Copy)]
pub struct ChunkConfig {
    /// Target chunk size in characters (token approximation: ~4 chars/token).
    pub target_chars: usize,
    /// Hard maximum — never exceed this even at sentence boundary.
    pub max_chars: usize,
    /// Chars to overlap between adjacent chunks (context bleed).
    pub overlap_chars: usize,
}

impl Default for ChunkConfig {
    fn default() -> Self {
        Self {
            target_chars: 2_000,   // ~500 tokens
            max_chars: 4_000,      // ~1000 tokens
            overlap_chars: 200,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Chunk {
    pub chunk_id: String,
    pub sequence: u32,
    pub text: String,
    /// Optional source location — page number or section heading.
    pub source_location: String,
}

/// Chunk a document. Pure function — no I/O, no panics on empty input.
pub fn chunk_text(text: &str, config: ChunkConfig) -> Vec<Chunk> {
    if text.trim().is_empty() {
        return Vec::new();
    }

    let mut chunks = Vec::new();
    let mut buffer = String::new();
    let mut sequence: u32 = 0;

    for sentence in text.unicode_sentences() {
        if buffer.len() + sentence.len() > config.target_chars && !buffer.is_empty() {
            push_chunk(&mut chunks, &mut buffer, &mut sequence, config.overlap_chars);
        }

        if sentence.len() > config.max_chars {
            // Sentence alone exceeds the hard limit — force-split.
            for slice in hard_split(sentence, config.max_chars) {
                push_chunk(&mut chunks, &mut String::from(slice), &mut sequence, 0);
            }
            continue;
        }

        buffer.push_str(sentence);
    }

    if !buffer.is_empty() {
        push_chunk(&mut chunks, &mut buffer, &mut sequence, 0);
    }

    chunks
}

fn push_chunk(out: &mut Vec<Chunk>, buf: &mut String, seq: &mut u32, overlap: usize) {
    let text = buf.trim().to_owned();
    if text.is_empty() {
        buf.clear();
        return;
    }

    out.push(Chunk {
        chunk_id: Uuid::new_v4().to_string(),
        sequence: *seq,
        text,
        source_location: String::new(),
    });
    *seq += 1;

    if overlap > 0 && buf.len() > overlap {
        let tail = buf[buf.len().saturating_sub(overlap)..].to_owned();
        buf.clear();
        buf.push_str(&tail);
    } else {
        buf.clear();
    }
}

fn hard_split(s: &str, max: usize) -> Vec<&str> {
    let mut out = Vec::new();
    let mut start = 0;
    let bytes = s.as_bytes();
    while start < bytes.len() {
        let end = (start + max).min(bytes.len());
        // Move `end` back to a char boundary.
        let mut e = end;
        while e > start && !s.is_char_boundary(e) {
            e -= 1;
        }
        if e == start {
            break;
        }
        out.push(&s[start..e]);
        start = e;
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_text_returns_empty() {
        assert!(chunk_text("", ChunkConfig::default()).is_empty());
        assert!(chunk_text("   \n\n  ", ChunkConfig::default()).is_empty());
    }

    #[test]
    fn short_text_one_chunk() {
        let text = "Hello world. This is a tiny doc.";
        let chunks = chunk_text(text, ChunkConfig::default());
        assert_eq!(chunks.len(), 1);
        assert!(chunks[0].text.contains("Hello"));
    }

    #[test]
    fn long_text_multiple_chunks() {
        let sentence = "Lorem ipsum dolor sit amet. ".repeat(200);
        let chunks = chunk_text(&sentence, ChunkConfig::default());
        assert!(chunks.len() > 1, "expected multiple chunks for long text");
    }
}
