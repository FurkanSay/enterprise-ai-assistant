package documents.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * DocumentsService — internal gRPC API for the Documents microservice.
 * Frontend uses REST via Gateway; this is for service-to-service calls.
 * Each RPC has its own request and response message even when the payload
 * would otherwise repeat. This keeps the API backwards-compatible when new
 * fields need to be added to a single RPC.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.66.0)",
    comments = "Source: documents/v1/documents.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class DocumentsServiceGrpc {

  private DocumentsServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "documents.v1.DocumentsService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentRequest,
      documents.v1.Documents.GetDocumentResponse> getGetDocumentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetDocument",
      requestType = documents.v1.Documents.GetDocumentRequest.class,
      responseType = documents.v1.Documents.GetDocumentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentRequest,
      documents.v1.Documents.GetDocumentResponse> getGetDocumentMethod() {
    io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentRequest, documents.v1.Documents.GetDocumentResponse> getGetDocumentMethod;
    if ((getGetDocumentMethod = DocumentsServiceGrpc.getGetDocumentMethod) == null) {
      synchronized (DocumentsServiceGrpc.class) {
        if ((getGetDocumentMethod = DocumentsServiceGrpc.getGetDocumentMethod) == null) {
          DocumentsServiceGrpc.getGetDocumentMethod = getGetDocumentMethod =
              io.grpc.MethodDescriptor.<documents.v1.Documents.GetDocumentRequest, documents.v1.Documents.GetDocumentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetDocument"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.GetDocumentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.GetDocumentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DocumentsServiceMethodDescriptorSupplier("GetDocument"))
              .build();
        }
      }
    }
    return getGetDocumentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<documents.v1.Documents.ListDocumentsRequest,
      documents.v1.Documents.ListDocumentsResponse> getListDocumentsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListDocuments",
      requestType = documents.v1.Documents.ListDocumentsRequest.class,
      responseType = documents.v1.Documents.ListDocumentsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<documents.v1.Documents.ListDocumentsRequest,
      documents.v1.Documents.ListDocumentsResponse> getListDocumentsMethod() {
    io.grpc.MethodDescriptor<documents.v1.Documents.ListDocumentsRequest, documents.v1.Documents.ListDocumentsResponse> getListDocumentsMethod;
    if ((getListDocumentsMethod = DocumentsServiceGrpc.getListDocumentsMethod) == null) {
      synchronized (DocumentsServiceGrpc.class) {
        if ((getListDocumentsMethod = DocumentsServiceGrpc.getListDocumentsMethod) == null) {
          DocumentsServiceGrpc.getListDocumentsMethod = getListDocumentsMethod =
              io.grpc.MethodDescriptor.<documents.v1.Documents.ListDocumentsRequest, documents.v1.Documents.ListDocumentsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListDocuments"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.ListDocumentsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.ListDocumentsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DocumentsServiceMethodDescriptorSupplier("ListDocuments"))
              .build();
        }
      }
    }
    return getListDocumentsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentDownloadUrlRequest,
      documents.v1.Documents.GetDocumentDownloadUrlResponse> getGetDocumentDownloadUrlMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetDocumentDownloadUrl",
      requestType = documents.v1.Documents.GetDocumentDownloadUrlRequest.class,
      responseType = documents.v1.Documents.GetDocumentDownloadUrlResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentDownloadUrlRequest,
      documents.v1.Documents.GetDocumentDownloadUrlResponse> getGetDocumentDownloadUrlMethod() {
    io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentDownloadUrlRequest, documents.v1.Documents.GetDocumentDownloadUrlResponse> getGetDocumentDownloadUrlMethod;
    if ((getGetDocumentDownloadUrlMethod = DocumentsServiceGrpc.getGetDocumentDownloadUrlMethod) == null) {
      synchronized (DocumentsServiceGrpc.class) {
        if ((getGetDocumentDownloadUrlMethod = DocumentsServiceGrpc.getGetDocumentDownloadUrlMethod) == null) {
          DocumentsServiceGrpc.getGetDocumentDownloadUrlMethod = getGetDocumentDownloadUrlMethod =
              io.grpc.MethodDescriptor.<documents.v1.Documents.GetDocumentDownloadUrlRequest, documents.v1.Documents.GetDocumentDownloadUrlResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetDocumentDownloadUrl"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.GetDocumentDownloadUrlRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.GetDocumentDownloadUrlResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DocumentsServiceMethodDescriptorSupplier("GetDocumentDownloadUrl"))
              .build();
        }
      }
    }
    return getGetDocumentDownloadUrlMethod;
  }

  private static volatile io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentTextRequest,
      documents.v1.Documents.GetDocumentTextResponse> getGetDocumentTextMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetDocumentText",
      requestType = documents.v1.Documents.GetDocumentTextRequest.class,
      responseType = documents.v1.Documents.GetDocumentTextResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentTextRequest,
      documents.v1.Documents.GetDocumentTextResponse> getGetDocumentTextMethod() {
    io.grpc.MethodDescriptor<documents.v1.Documents.GetDocumentTextRequest, documents.v1.Documents.GetDocumentTextResponse> getGetDocumentTextMethod;
    if ((getGetDocumentTextMethod = DocumentsServiceGrpc.getGetDocumentTextMethod) == null) {
      synchronized (DocumentsServiceGrpc.class) {
        if ((getGetDocumentTextMethod = DocumentsServiceGrpc.getGetDocumentTextMethod) == null) {
          DocumentsServiceGrpc.getGetDocumentTextMethod = getGetDocumentTextMethod =
              io.grpc.MethodDescriptor.<documents.v1.Documents.GetDocumentTextRequest, documents.v1.Documents.GetDocumentTextResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetDocumentText"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.GetDocumentTextRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  documents.v1.Documents.GetDocumentTextResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DocumentsServiceMethodDescriptorSupplier("GetDocumentText"))
              .build();
        }
      }
    }
    return getGetDocumentTextMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DocumentsServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DocumentsServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DocumentsServiceStub>() {
        @java.lang.Override
        public DocumentsServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DocumentsServiceStub(channel, callOptions);
        }
      };
    return DocumentsServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DocumentsServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DocumentsServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DocumentsServiceBlockingStub>() {
        @java.lang.Override
        public DocumentsServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DocumentsServiceBlockingStub(channel, callOptions);
        }
      };
    return DocumentsServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DocumentsServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DocumentsServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DocumentsServiceFutureStub>() {
        @java.lang.Override
        public DocumentsServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DocumentsServiceFutureStub(channel, callOptions);
        }
      };
    return DocumentsServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * DocumentsService — internal gRPC API for the Documents microservice.
   * Frontend uses REST via Gateway; this is for service-to-service calls.
   * Each RPC has its own request and response message even when the payload
   * would otherwise repeat. This keeps the API backwards-compatible when new
   * fields need to be added to a single RPC.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Fetch document metadata by ID. Tenant-scoped (RLS enforced).
     * </pre>
     */
    default void getDocument(documents.v1.Documents.GetDocumentRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetDocumentMethod(), responseObserver);
    }

    /**
     * <pre>
     * List documents for the current tenant.
     * </pre>
     */
    default void listDocuments(documents.v1.Documents.ListDocumentsRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.ListDocumentsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListDocumentsMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get presigned download URL for the original file (MinIO).
     * </pre>
     */
    default void getDocumentDownloadUrl(documents.v1.Documents.GetDocumentDownloadUrlRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentDownloadUrlResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetDocumentDownloadUrlMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get full extracted text of a document (used by Processing for chunking).
     * Returns text stream for memory efficiency on large docs.
     * </pre>
     */
    default void getDocumentText(documents.v1.Documents.GetDocumentTextRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentTextResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetDocumentTextMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service DocumentsService.
   * <pre>
   * DocumentsService — internal gRPC API for the Documents microservice.
   * Frontend uses REST via Gateway; this is for service-to-service calls.
   * Each RPC has its own request and response message even when the payload
   * would otherwise repeat. This keeps the API backwards-compatible when new
   * fields need to be added to a single RPC.
   * </pre>
   */
  public static abstract class DocumentsServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return DocumentsServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service DocumentsService.
   * <pre>
   * DocumentsService — internal gRPC API for the Documents microservice.
   * Frontend uses REST via Gateway; this is for service-to-service calls.
   * Each RPC has its own request and response message even when the payload
   * would otherwise repeat. This keeps the API backwards-compatible when new
   * fields need to be added to a single RPC.
   * </pre>
   */
  public static final class DocumentsServiceStub
      extends io.grpc.stub.AbstractAsyncStub<DocumentsServiceStub> {
    private DocumentsServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DocumentsServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DocumentsServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Fetch document metadata by ID. Tenant-scoped (RLS enforced).
     * </pre>
     */
    public void getDocument(documents.v1.Documents.GetDocumentRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetDocumentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * List documents for the current tenant.
     * </pre>
     */
    public void listDocuments(documents.v1.Documents.ListDocumentsRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.ListDocumentsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListDocumentsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get presigned download URL for the original file (MinIO).
     * </pre>
     */
    public void getDocumentDownloadUrl(documents.v1.Documents.GetDocumentDownloadUrlRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentDownloadUrlResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetDocumentDownloadUrlMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get full extracted text of a document (used by Processing for chunking).
     * Returns text stream for memory efficiency on large docs.
     * </pre>
     */
    public void getDocumentText(documents.v1.Documents.GetDocumentTextRequest request,
        io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentTextResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getGetDocumentTextMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service DocumentsService.
   * <pre>
   * DocumentsService — internal gRPC API for the Documents microservice.
   * Frontend uses REST via Gateway; this is for service-to-service calls.
   * Each RPC has its own request and response message even when the payload
   * would otherwise repeat. This keeps the API backwards-compatible when new
   * fields need to be added to a single RPC.
   * </pre>
   */
  public static final class DocumentsServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<DocumentsServiceBlockingStub> {
    private DocumentsServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DocumentsServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DocumentsServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Fetch document metadata by ID. Tenant-scoped (RLS enforced).
     * </pre>
     */
    public documents.v1.Documents.GetDocumentResponse getDocument(documents.v1.Documents.GetDocumentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetDocumentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List documents for the current tenant.
     * </pre>
     */
    public documents.v1.Documents.ListDocumentsResponse listDocuments(documents.v1.Documents.ListDocumentsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListDocumentsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get presigned download URL for the original file (MinIO).
     * </pre>
     */
    public documents.v1.Documents.GetDocumentDownloadUrlResponse getDocumentDownloadUrl(documents.v1.Documents.GetDocumentDownloadUrlRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetDocumentDownloadUrlMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get full extracted text of a document (used by Processing for chunking).
     * Returns text stream for memory efficiency on large docs.
     * </pre>
     */
    public java.util.Iterator<documents.v1.Documents.GetDocumentTextResponse> getDocumentText(
        documents.v1.Documents.GetDocumentTextRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getGetDocumentTextMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service DocumentsService.
   * <pre>
   * DocumentsService — internal gRPC API for the Documents microservice.
   * Frontend uses REST via Gateway; this is for service-to-service calls.
   * Each RPC has its own request and response message even when the payload
   * would otherwise repeat. This keeps the API backwards-compatible when new
   * fields need to be added to a single RPC.
   * </pre>
   */
  public static final class DocumentsServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<DocumentsServiceFutureStub> {
    private DocumentsServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DocumentsServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DocumentsServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Fetch document metadata by ID. Tenant-scoped (RLS enforced).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<documents.v1.Documents.GetDocumentResponse> getDocument(
        documents.v1.Documents.GetDocumentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetDocumentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * List documents for the current tenant.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<documents.v1.Documents.ListDocumentsResponse> listDocuments(
        documents.v1.Documents.ListDocumentsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListDocumentsMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get presigned download URL for the original file (MinIO).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<documents.v1.Documents.GetDocumentDownloadUrlResponse> getDocumentDownloadUrl(
        documents.v1.Documents.GetDocumentDownloadUrlRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetDocumentDownloadUrlMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_DOCUMENT = 0;
  private static final int METHODID_LIST_DOCUMENTS = 1;
  private static final int METHODID_GET_DOCUMENT_DOWNLOAD_URL = 2;
  private static final int METHODID_GET_DOCUMENT_TEXT = 3;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_DOCUMENT:
          serviceImpl.getDocument((documents.v1.Documents.GetDocumentRequest) request,
              (io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentResponse>) responseObserver);
          break;
        case METHODID_LIST_DOCUMENTS:
          serviceImpl.listDocuments((documents.v1.Documents.ListDocumentsRequest) request,
              (io.grpc.stub.StreamObserver<documents.v1.Documents.ListDocumentsResponse>) responseObserver);
          break;
        case METHODID_GET_DOCUMENT_DOWNLOAD_URL:
          serviceImpl.getDocumentDownloadUrl((documents.v1.Documents.GetDocumentDownloadUrlRequest) request,
              (io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentDownloadUrlResponse>) responseObserver);
          break;
        case METHODID_GET_DOCUMENT_TEXT:
          serviceImpl.getDocumentText((documents.v1.Documents.GetDocumentTextRequest) request,
              (io.grpc.stub.StreamObserver<documents.v1.Documents.GetDocumentTextResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getGetDocumentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              documents.v1.Documents.GetDocumentRequest,
              documents.v1.Documents.GetDocumentResponse>(
                service, METHODID_GET_DOCUMENT)))
        .addMethod(
          getListDocumentsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              documents.v1.Documents.ListDocumentsRequest,
              documents.v1.Documents.ListDocumentsResponse>(
                service, METHODID_LIST_DOCUMENTS)))
        .addMethod(
          getGetDocumentDownloadUrlMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              documents.v1.Documents.GetDocumentDownloadUrlRequest,
              documents.v1.Documents.GetDocumentDownloadUrlResponse>(
                service, METHODID_GET_DOCUMENT_DOWNLOAD_URL)))
        .addMethod(
          getGetDocumentTextMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              documents.v1.Documents.GetDocumentTextRequest,
              documents.v1.Documents.GetDocumentTextResponse>(
                service, METHODID_GET_DOCUMENT_TEXT)))
        .build();
  }

  private static abstract class DocumentsServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DocumentsServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return documents.v1.Documents.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DocumentsService");
    }
  }

  private static final class DocumentsServiceFileDescriptorSupplier
      extends DocumentsServiceBaseDescriptorSupplier {
    DocumentsServiceFileDescriptorSupplier() {}
  }

  private static final class DocumentsServiceMethodDescriptorSupplier
      extends DocumentsServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    DocumentsServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (DocumentsServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DocumentsServiceFileDescriptorSupplier())
              .addMethod(getGetDocumentMethod())
              .addMethod(getListDocumentsMethod())
              .addMethod(getGetDocumentDownloadUrlMethod())
              .addMethod(getGetDocumentTextMethod())
              .build();
        }
      }
    }
    return result;
  }
}
