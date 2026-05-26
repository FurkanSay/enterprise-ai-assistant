package processing.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * ProcessingService — Rust microservice for CPU-bound chunking + BM25 search.
 * AI Engine calls this synchronously for hybrid retrieval (Qdrant dense + BM25 sparse).
 * Documents service does NOT call this directly; chunking is event-driven via Redis.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.66.0)",
    comments = "Source: processing/v1/processing.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ProcessingServiceGrpc {

  private ProcessingServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "processing.v1.ProcessingService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<processing.v1.Processing.BM25SearchRequest,
      processing.v1.Processing.BM25SearchResponse> getBM25SearchMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BM25Search",
      requestType = processing.v1.Processing.BM25SearchRequest.class,
      responseType = processing.v1.Processing.BM25SearchResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<processing.v1.Processing.BM25SearchRequest,
      processing.v1.Processing.BM25SearchResponse> getBM25SearchMethod() {
    io.grpc.MethodDescriptor<processing.v1.Processing.BM25SearchRequest, processing.v1.Processing.BM25SearchResponse> getBM25SearchMethod;
    if ((getBM25SearchMethod = ProcessingServiceGrpc.getBM25SearchMethod) == null) {
      synchronized (ProcessingServiceGrpc.class) {
        if ((getBM25SearchMethod = ProcessingServiceGrpc.getBM25SearchMethod) == null) {
          ProcessingServiceGrpc.getBM25SearchMethod = getBM25SearchMethod =
              io.grpc.MethodDescriptor.<processing.v1.Processing.BM25SearchRequest, processing.v1.Processing.BM25SearchResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "BM25Search"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  processing.v1.Processing.BM25SearchRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  processing.v1.Processing.BM25SearchResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessingServiceMethodDescriptorSupplier("BM25Search"))
              .build();
        }
      }
    }
    return getBM25SearchMethod;
  }

  private static volatile io.grpc.MethodDescriptor<processing.v1.Processing.ReindexRequest,
      processing.v1.Processing.ReindexResponse> getReindexMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Reindex",
      requestType = processing.v1.Processing.ReindexRequest.class,
      responseType = processing.v1.Processing.ReindexResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<processing.v1.Processing.ReindexRequest,
      processing.v1.Processing.ReindexResponse> getReindexMethod() {
    io.grpc.MethodDescriptor<processing.v1.Processing.ReindexRequest, processing.v1.Processing.ReindexResponse> getReindexMethod;
    if ((getReindexMethod = ProcessingServiceGrpc.getReindexMethod) == null) {
      synchronized (ProcessingServiceGrpc.class) {
        if ((getReindexMethod = ProcessingServiceGrpc.getReindexMethod) == null) {
          ProcessingServiceGrpc.getReindexMethod = getReindexMethod =
              io.grpc.MethodDescriptor.<processing.v1.Processing.ReindexRequest, processing.v1.Processing.ReindexResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Reindex"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  processing.v1.Processing.ReindexRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  processing.v1.Processing.ReindexResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessingServiceMethodDescriptorSupplier("Reindex"))
              .build();
        }
      }
    }
    return getReindexMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ProcessingServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProcessingServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProcessingServiceStub>() {
        @java.lang.Override
        public ProcessingServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProcessingServiceStub(channel, callOptions);
        }
      };
    return ProcessingServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ProcessingServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProcessingServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProcessingServiceBlockingStub>() {
        @java.lang.Override
        public ProcessingServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProcessingServiceBlockingStub(channel, callOptions);
        }
      };
    return ProcessingServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ProcessingServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProcessingServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProcessingServiceFutureStub>() {
        @java.lang.Override
        public ProcessingServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProcessingServiceFutureStub(channel, callOptions);
        }
      };
    return ProcessingServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * ProcessingService — Rust microservice for CPU-bound chunking + BM25 search.
   * AI Engine calls this synchronously for hybrid retrieval (Qdrant dense + BM25 sparse).
   * Documents service does NOT call this directly; chunking is event-driven via Redis.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * BM25 keyword search over indexed document chunks.
     * Tenant-scoped — only returns chunks belonging to the calling tenant.
     * </pre>
     */
    default void bM25Search(processing.v1.Processing.BM25SearchRequest request,
        io.grpc.stub.StreamObserver<processing.v1.Processing.BM25SearchResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBM25SearchMethod(), responseObserver);
    }

    /**
     * <pre>
     * Re-index a document (used after document edit or chunking strategy change).
     * Async-fire-and-return: actual work happens via Redis event consumer.
     * </pre>
     */
    default void reindex(processing.v1.Processing.ReindexRequest request,
        io.grpc.stub.StreamObserver<processing.v1.Processing.ReindexResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReindexMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ProcessingService.
   * <pre>
   * ProcessingService — Rust microservice for CPU-bound chunking + BM25 search.
   * AI Engine calls this synchronously for hybrid retrieval (Qdrant dense + BM25 sparse).
   * Documents service does NOT call this directly; chunking is event-driven via Redis.
   * </pre>
   */
  public static abstract class ProcessingServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ProcessingServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ProcessingService.
   * <pre>
   * ProcessingService — Rust microservice for CPU-bound chunking + BM25 search.
   * AI Engine calls this synchronously for hybrid retrieval (Qdrant dense + BM25 sparse).
   * Documents service does NOT call this directly; chunking is event-driven via Redis.
   * </pre>
   */
  public static final class ProcessingServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ProcessingServiceStub> {
    private ProcessingServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProcessingServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProcessingServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * BM25 keyword search over indexed document chunks.
     * Tenant-scoped — only returns chunks belonging to the calling tenant.
     * </pre>
     */
    public void bM25Search(processing.v1.Processing.BM25SearchRequest request,
        io.grpc.stub.StreamObserver<processing.v1.Processing.BM25SearchResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBM25SearchMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Re-index a document (used after document edit or chunking strategy change).
     * Async-fire-and-return: actual work happens via Redis event consumer.
     * </pre>
     */
    public void reindex(processing.v1.Processing.ReindexRequest request,
        io.grpc.stub.StreamObserver<processing.v1.Processing.ReindexResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReindexMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ProcessingService.
   * <pre>
   * ProcessingService — Rust microservice for CPU-bound chunking + BM25 search.
   * AI Engine calls this synchronously for hybrid retrieval (Qdrant dense + BM25 sparse).
   * Documents service does NOT call this directly; chunking is event-driven via Redis.
   * </pre>
   */
  public static final class ProcessingServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ProcessingServiceBlockingStub> {
    private ProcessingServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProcessingServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProcessingServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * BM25 keyword search over indexed document chunks.
     * Tenant-scoped — only returns chunks belonging to the calling tenant.
     * </pre>
     */
    public processing.v1.Processing.BM25SearchResponse bM25Search(processing.v1.Processing.BM25SearchRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBM25SearchMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Re-index a document (used after document edit or chunking strategy change).
     * Async-fire-and-return: actual work happens via Redis event consumer.
     * </pre>
     */
    public processing.v1.Processing.ReindexResponse reindex(processing.v1.Processing.ReindexRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReindexMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ProcessingService.
   * <pre>
   * ProcessingService — Rust microservice for CPU-bound chunking + BM25 search.
   * AI Engine calls this synchronously for hybrid retrieval (Qdrant dense + BM25 sparse).
   * Documents service does NOT call this directly; chunking is event-driven via Redis.
   * </pre>
   */
  public static final class ProcessingServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ProcessingServiceFutureStub> {
    private ProcessingServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProcessingServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProcessingServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * BM25 keyword search over indexed document chunks.
     * Tenant-scoped — only returns chunks belonging to the calling tenant.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<processing.v1.Processing.BM25SearchResponse> bM25Search(
        processing.v1.Processing.BM25SearchRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBM25SearchMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Re-index a document (used after document edit or chunking strategy change).
     * Async-fire-and-return: actual work happens via Redis event consumer.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<processing.v1.Processing.ReindexResponse> reindex(
        processing.v1.Processing.ReindexRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReindexMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_BM25SEARCH = 0;
  private static final int METHODID_REINDEX = 1;

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
        case METHODID_BM25SEARCH:
          serviceImpl.bM25Search((processing.v1.Processing.BM25SearchRequest) request,
              (io.grpc.stub.StreamObserver<processing.v1.Processing.BM25SearchResponse>) responseObserver);
          break;
        case METHODID_REINDEX:
          serviceImpl.reindex((processing.v1.Processing.ReindexRequest) request,
              (io.grpc.stub.StreamObserver<processing.v1.Processing.ReindexResponse>) responseObserver);
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
          getBM25SearchMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              processing.v1.Processing.BM25SearchRequest,
              processing.v1.Processing.BM25SearchResponse>(
                service, METHODID_BM25SEARCH)))
        .addMethod(
          getReindexMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              processing.v1.Processing.ReindexRequest,
              processing.v1.Processing.ReindexResponse>(
                service, METHODID_REINDEX)))
        .build();
  }

  private static abstract class ProcessingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ProcessingServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return processing.v1.Processing.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ProcessingService");
    }
  }

  private static final class ProcessingServiceFileDescriptorSupplier
      extends ProcessingServiceBaseDescriptorSupplier {
    ProcessingServiceFileDescriptorSupplier() {}
  }

  private static final class ProcessingServiceMethodDescriptorSupplier
      extends ProcessingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ProcessingServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ProcessingServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ProcessingServiceFileDescriptorSupplier())
              .addMethod(getBM25SearchMethod())
              .addMethod(getReindexMethod())
              .build();
        }
      }
    }
    return result;
  }
}
