package aiengine.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * AiEngineService — most AI Engine endpoints are REST/SSE (browser-facing).
 * This gRPC service exists for two cases:
 *   1. Internal triggers from other services (e.g. background re-embed batch)
 *   2. Future: agent-to-agent calls (sub-agent orchestration)
 * Each RPC owns its request and response message so new fields can be added
 * to a single RPC without affecting the others.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.66.0)",
    comments = "Source: aiengine/v1/aiengine.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AiEngineServiceGrpc {

  private AiEngineServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "aiengine.v1.AiEngineService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<aiengine.v1.Aiengine.EmbedChunksRequest,
      aiengine.v1.Aiengine.EmbedChunksResponse> getEmbedChunksMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "EmbedChunks",
      requestType = aiengine.v1.Aiengine.EmbedChunksRequest.class,
      responseType = aiengine.v1.Aiengine.EmbedChunksResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<aiengine.v1.Aiengine.EmbedChunksRequest,
      aiengine.v1.Aiengine.EmbedChunksResponse> getEmbedChunksMethod() {
    io.grpc.MethodDescriptor<aiengine.v1.Aiengine.EmbedChunksRequest, aiengine.v1.Aiengine.EmbedChunksResponse> getEmbedChunksMethod;
    if ((getEmbedChunksMethod = AiEngineServiceGrpc.getEmbedChunksMethod) == null) {
      synchronized (AiEngineServiceGrpc.class) {
        if ((getEmbedChunksMethod = AiEngineServiceGrpc.getEmbedChunksMethod) == null) {
          AiEngineServiceGrpc.getEmbedChunksMethod = getEmbedChunksMethod =
              io.grpc.MethodDescriptor.<aiengine.v1.Aiengine.EmbedChunksRequest, aiengine.v1.Aiengine.EmbedChunksResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "EmbedChunks"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  aiengine.v1.Aiengine.EmbedChunksRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  aiengine.v1.Aiengine.EmbedChunksResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AiEngineServiceMethodDescriptorSupplier("EmbedChunks"))
              .build();
        }
      }
    }
    return getEmbedChunksMethod;
  }

  private static volatile io.grpc.MethodDescriptor<aiengine.v1.Aiengine.GetSessionRequest,
      aiengine.v1.Aiengine.GetSessionResponse> getGetSessionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetSession",
      requestType = aiengine.v1.Aiengine.GetSessionRequest.class,
      responseType = aiengine.v1.Aiengine.GetSessionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<aiengine.v1.Aiengine.GetSessionRequest,
      aiengine.v1.Aiengine.GetSessionResponse> getGetSessionMethod() {
    io.grpc.MethodDescriptor<aiengine.v1.Aiengine.GetSessionRequest, aiengine.v1.Aiengine.GetSessionResponse> getGetSessionMethod;
    if ((getGetSessionMethod = AiEngineServiceGrpc.getGetSessionMethod) == null) {
      synchronized (AiEngineServiceGrpc.class) {
        if ((getGetSessionMethod = AiEngineServiceGrpc.getGetSessionMethod) == null) {
          AiEngineServiceGrpc.getGetSessionMethod = getGetSessionMethod =
              io.grpc.MethodDescriptor.<aiengine.v1.Aiengine.GetSessionRequest, aiengine.v1.Aiengine.GetSessionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetSession"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  aiengine.v1.Aiengine.GetSessionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  aiengine.v1.Aiengine.GetSessionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AiEngineServiceMethodDescriptorSupplier("GetSession"))
              .build();
        }
      }
    }
    return getGetSessionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AiEngineServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AiEngineServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AiEngineServiceStub>() {
        @java.lang.Override
        public AiEngineServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AiEngineServiceStub(channel, callOptions);
        }
      };
    return AiEngineServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AiEngineServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AiEngineServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AiEngineServiceBlockingStub>() {
        @java.lang.Override
        public AiEngineServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AiEngineServiceBlockingStub(channel, callOptions);
        }
      };
    return AiEngineServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AiEngineServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AiEngineServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AiEngineServiceFutureStub>() {
        @java.lang.Override
        public AiEngineServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AiEngineServiceFutureStub(channel, callOptions);
        }
      };
    return AiEngineServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * AiEngineService — most AI Engine endpoints are REST/SSE (browser-facing).
   * This gRPC service exists for two cases:
   *   1. Internal triggers from other services (e.g. background re-embed batch)
   *   2. Future: agent-to-agent calls (sub-agent orchestration)
   * Each RPC owns its request and response message so new fields can be added
   * to a single RPC without affecting the others.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Trigger embedding for a batch of chunks (called by Processing after chunking).
     * </pre>
     */
    default void embedChunks(aiengine.v1.Aiengine.EmbedChunksRequest request,
        io.grpc.stub.StreamObserver<aiengine.v1.Aiengine.EmbedChunksResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getEmbedChunksMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get session metadata (read-only).
     * </pre>
     */
    default void getSession(aiengine.v1.Aiengine.GetSessionRequest request,
        io.grpc.stub.StreamObserver<aiengine.v1.Aiengine.GetSessionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetSessionMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AiEngineService.
   * <pre>
   * AiEngineService — most AI Engine endpoints are REST/SSE (browser-facing).
   * This gRPC service exists for two cases:
   *   1. Internal triggers from other services (e.g. background re-embed batch)
   *   2. Future: agent-to-agent calls (sub-agent orchestration)
   * Each RPC owns its request and response message so new fields can be added
   * to a single RPC without affecting the others.
   * </pre>
   */
  public static abstract class AiEngineServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AiEngineServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AiEngineService.
   * <pre>
   * AiEngineService — most AI Engine endpoints are REST/SSE (browser-facing).
   * This gRPC service exists for two cases:
   *   1. Internal triggers from other services (e.g. background re-embed batch)
   *   2. Future: agent-to-agent calls (sub-agent orchestration)
   * Each RPC owns its request and response message so new fields can be added
   * to a single RPC without affecting the others.
   * </pre>
   */
  public static final class AiEngineServiceStub
      extends io.grpc.stub.AbstractAsyncStub<AiEngineServiceStub> {
    private AiEngineServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AiEngineServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AiEngineServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Trigger embedding for a batch of chunks (called by Processing after chunking).
     * </pre>
     */
    public void embedChunks(aiengine.v1.Aiengine.EmbedChunksRequest request,
        io.grpc.stub.StreamObserver<aiengine.v1.Aiengine.EmbedChunksResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getEmbedChunksMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get session metadata (read-only).
     * </pre>
     */
    public void getSession(aiengine.v1.Aiengine.GetSessionRequest request,
        io.grpc.stub.StreamObserver<aiengine.v1.Aiengine.GetSessionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetSessionMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AiEngineService.
   * <pre>
   * AiEngineService — most AI Engine endpoints are REST/SSE (browser-facing).
   * This gRPC service exists for two cases:
   *   1. Internal triggers from other services (e.g. background re-embed batch)
   *   2. Future: agent-to-agent calls (sub-agent orchestration)
   * Each RPC owns its request and response message so new fields can be added
   * to a single RPC without affecting the others.
   * </pre>
   */
  public static final class AiEngineServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AiEngineServiceBlockingStub> {
    private AiEngineServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AiEngineServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AiEngineServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Trigger embedding for a batch of chunks (called by Processing after chunking).
     * </pre>
     */
    public aiengine.v1.Aiengine.EmbedChunksResponse embedChunks(aiengine.v1.Aiengine.EmbedChunksRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getEmbedChunksMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get session metadata (read-only).
     * </pre>
     */
    public aiengine.v1.Aiengine.GetSessionResponse getSession(aiengine.v1.Aiengine.GetSessionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetSessionMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AiEngineService.
   * <pre>
   * AiEngineService — most AI Engine endpoints are REST/SSE (browser-facing).
   * This gRPC service exists for two cases:
   *   1. Internal triggers from other services (e.g. background re-embed batch)
   *   2. Future: agent-to-agent calls (sub-agent orchestration)
   * Each RPC owns its request and response message so new fields can be added
   * to a single RPC without affecting the others.
   * </pre>
   */
  public static final class AiEngineServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<AiEngineServiceFutureStub> {
    private AiEngineServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AiEngineServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AiEngineServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Trigger embedding for a batch of chunks (called by Processing after chunking).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<aiengine.v1.Aiengine.EmbedChunksResponse> embedChunks(
        aiengine.v1.Aiengine.EmbedChunksRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getEmbedChunksMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get session metadata (read-only).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<aiengine.v1.Aiengine.GetSessionResponse> getSession(
        aiengine.v1.Aiengine.GetSessionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetSessionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_EMBED_CHUNKS = 0;
  private static final int METHODID_GET_SESSION = 1;

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
        case METHODID_EMBED_CHUNKS:
          serviceImpl.embedChunks((aiengine.v1.Aiengine.EmbedChunksRequest) request,
              (io.grpc.stub.StreamObserver<aiengine.v1.Aiengine.EmbedChunksResponse>) responseObserver);
          break;
        case METHODID_GET_SESSION:
          serviceImpl.getSession((aiengine.v1.Aiengine.GetSessionRequest) request,
              (io.grpc.stub.StreamObserver<aiengine.v1.Aiengine.GetSessionResponse>) responseObserver);
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
          getEmbedChunksMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              aiengine.v1.Aiengine.EmbedChunksRequest,
              aiengine.v1.Aiengine.EmbedChunksResponse>(
                service, METHODID_EMBED_CHUNKS)))
        .addMethod(
          getGetSessionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              aiengine.v1.Aiengine.GetSessionRequest,
              aiengine.v1.Aiengine.GetSessionResponse>(
                service, METHODID_GET_SESSION)))
        .build();
  }

  private static abstract class AiEngineServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AiEngineServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return aiengine.v1.Aiengine.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AiEngineService");
    }
  }

  private static final class AiEngineServiceFileDescriptorSupplier
      extends AiEngineServiceBaseDescriptorSupplier {
    AiEngineServiceFileDescriptorSupplier() {}
  }

  private static final class AiEngineServiceMethodDescriptorSupplier
      extends AiEngineServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    AiEngineServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (AiEngineServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AiEngineServiceFileDescriptorSupplier())
              .addMethod(getEmbedChunksMethod())
              .addMethod(getGetSessionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
