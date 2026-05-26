//! Generated gRPC + protobuf stubs for the Enterprise AI Assistant.
//!
//! Do not edit files at the crate root by hand — they are produced by
//! `make proto` at the repository root. Source of truth: `../../../protos/`.
//!
//! Each `.rs` file is the body of a Rust module that mirrors a single
//! proto package path. The tonic-generated client/server code lives in
//! `<pkg>.v1.tonic.rs` next to the message definitions; we include both
//! into the same module so the gRPC types can reach the message types
//! without `super::` qualifications.

#![allow(clippy::all, missing_docs, non_snake_case)]

pub mod common {
    pub mod v1 {
        include!("../common.v1.rs");
    }
}

pub mod documents {
    pub mod v1 {
        include!("../documents.v1.rs");
        include!("../documents.v1.tonic.rs");
    }
}

pub mod processing {
    pub mod v1 {
        include!("../processing.v1.rs");
        include!("../processing.v1.tonic.rs");
    }
}

pub mod aiengine {
    pub mod v1 {
        include!("../aiengine.v1.rs");
        include!("../aiengine.v1.tonic.rs");
    }
}

pub mod identity {
    pub mod v1 {
        include!("../identity.v1.rs");
        include!("../identity.v1.tonic.rs");
    }
}
