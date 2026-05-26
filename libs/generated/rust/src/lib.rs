//! Generated gRPC + protobuf stubs for the Enterprise AI Assistant.
//!
//! Do not edit files at the crate root by hand — they are produced by
//! `make proto` at the repository root. Source of truth: `../../../protos/`.
//!
//! Each `.rs` file mirrors a single proto package path. The prost-emitted
//! `<pkg>.v1.rs` already pulls in the matching tonic file via its own
//! `include!("<pkg>.v1.tonic.rs")` at the bottom, so we only include the
//! prost file here — including tonic again would duplicate the
//! `<service>_client` / `<service>_server` modules and fail to compile.

#![allow(clippy::all, missing_docs, non_snake_case)]

pub mod common {
    pub mod v1 {
        include!("../common.v1.rs");
    }
}

pub mod documents {
    pub mod v1 {
        include!("../documents.v1.rs");
    }
}

pub mod processing {
    pub mod v1 {
        include!("../processing.v1.rs");
    }
}

pub mod aiengine {
    pub mod v1 {
        include!("../aiengine.v1.rs");
    }
}

pub mod identity {
    pub mod v1 {
        include!("../identity.v1.rs");
    }
}
