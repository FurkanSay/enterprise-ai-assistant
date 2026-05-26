package identity.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * IdentityService — internal gRPC API for the Identity microservice.
 * Gateway calls these during JWT validation and authorization.
 * End-user auth flows (login, refresh) are REST/OIDC (not here).
 * Each RPC has its own request and response message, even when the
 * payload would otherwise repeat, so that new fields can be added per RPC
 * without breaking the others.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.66.0)",
    comments = "Source: identity/v1/identity.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class IdentityServiceGrpc {

  private IdentityServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "identity.v1.IdentityService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<identity.v1.Identity.ValidateTokenRequest,
      identity.v1.Identity.ValidateTokenResponse> getValidateTokenMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ValidateToken",
      requestType = identity.v1.Identity.ValidateTokenRequest.class,
      responseType = identity.v1.Identity.ValidateTokenResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<identity.v1.Identity.ValidateTokenRequest,
      identity.v1.Identity.ValidateTokenResponse> getValidateTokenMethod() {
    io.grpc.MethodDescriptor<identity.v1.Identity.ValidateTokenRequest, identity.v1.Identity.ValidateTokenResponse> getValidateTokenMethod;
    if ((getValidateTokenMethod = IdentityServiceGrpc.getValidateTokenMethod) == null) {
      synchronized (IdentityServiceGrpc.class) {
        if ((getValidateTokenMethod = IdentityServiceGrpc.getValidateTokenMethod) == null) {
          IdentityServiceGrpc.getValidateTokenMethod = getValidateTokenMethod =
              io.grpc.MethodDescriptor.<identity.v1.Identity.ValidateTokenRequest, identity.v1.Identity.ValidateTokenResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ValidateToken"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  identity.v1.Identity.ValidateTokenRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  identity.v1.Identity.ValidateTokenResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityServiceMethodDescriptorSupplier("ValidateToken"))
              .build();
        }
      }
    }
    return getValidateTokenMethod;
  }

  private static volatile io.grpc.MethodDescriptor<identity.v1.Identity.GetUserRequest,
      identity.v1.Identity.GetUserResponse> getGetUserMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetUser",
      requestType = identity.v1.Identity.GetUserRequest.class,
      responseType = identity.v1.Identity.GetUserResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<identity.v1.Identity.GetUserRequest,
      identity.v1.Identity.GetUserResponse> getGetUserMethod() {
    io.grpc.MethodDescriptor<identity.v1.Identity.GetUserRequest, identity.v1.Identity.GetUserResponse> getGetUserMethod;
    if ((getGetUserMethod = IdentityServiceGrpc.getGetUserMethod) == null) {
      synchronized (IdentityServiceGrpc.class) {
        if ((getGetUserMethod = IdentityServiceGrpc.getGetUserMethod) == null) {
          IdentityServiceGrpc.getGetUserMethod = getGetUserMethod =
              io.grpc.MethodDescriptor.<identity.v1.Identity.GetUserRequest, identity.v1.Identity.GetUserResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetUser"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  identity.v1.Identity.GetUserRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  identity.v1.Identity.GetUserResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityServiceMethodDescriptorSupplier("GetUser"))
              .build();
        }
      }
    }
    return getGetUserMethod;
  }

  private static volatile io.grpc.MethodDescriptor<identity.v1.Identity.GetTenantRequest,
      identity.v1.Identity.GetTenantResponse> getGetTenantMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetTenant",
      requestType = identity.v1.Identity.GetTenantRequest.class,
      responseType = identity.v1.Identity.GetTenantResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<identity.v1.Identity.GetTenantRequest,
      identity.v1.Identity.GetTenantResponse> getGetTenantMethod() {
    io.grpc.MethodDescriptor<identity.v1.Identity.GetTenantRequest, identity.v1.Identity.GetTenantResponse> getGetTenantMethod;
    if ((getGetTenantMethod = IdentityServiceGrpc.getGetTenantMethod) == null) {
      synchronized (IdentityServiceGrpc.class) {
        if ((getGetTenantMethod = IdentityServiceGrpc.getGetTenantMethod) == null) {
          IdentityServiceGrpc.getGetTenantMethod = getGetTenantMethod =
              io.grpc.MethodDescriptor.<identity.v1.Identity.GetTenantRequest, identity.v1.Identity.GetTenantResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetTenant"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  identity.v1.Identity.GetTenantRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  identity.v1.Identity.GetTenantResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityServiceMethodDescriptorSupplier("GetTenant"))
              .build();
        }
      }
    }
    return getGetTenantMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static IdentityServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityServiceStub>() {
        @java.lang.Override
        public IdentityServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityServiceStub(channel, callOptions);
        }
      };
    return IdentityServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static IdentityServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityServiceBlockingStub>() {
        @java.lang.Override
        public IdentityServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityServiceBlockingStub(channel, callOptions);
        }
      };
    return IdentityServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static IdentityServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityServiceFutureStub>() {
        @java.lang.Override
        public IdentityServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityServiceFutureStub(channel, callOptions);
        }
      };
    return IdentityServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * IdentityService — internal gRPC API for the Identity microservice.
   * Gateway calls these during JWT validation and authorization.
   * End-user auth flows (login, refresh) are REST/OIDC (not here).
   * Each RPC has its own request and response message, even when the
   * payload would otherwise repeat, so that new fields can be added per RPC
   * without breaking the others.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Validate JWT and resolve to a TenantContext.
     * Gateway calls this on every protected request.
     * </pre>
     */
    default void validateToken(identity.v1.Identity.ValidateTokenRequest request,
        io.grpc.stub.StreamObserver<identity.v1.Identity.ValidateTokenResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getValidateTokenMethod(), responseObserver);
    }

    /**
     * <pre>
     * Look up user by ID. Used for audit log enrichment.
     * </pre>
     */
    default void getUser(identity.v1.Identity.GetUserRequest request,
        io.grpc.stub.StreamObserver<identity.v1.Identity.GetUserResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetUserMethod(), responseObserver);
    }

    /**
     * <pre>
     * Look up tenant by ID. Used for tenant quota / config lookups.
     * </pre>
     */
    default void getTenant(identity.v1.Identity.GetTenantRequest request,
        io.grpc.stub.StreamObserver<identity.v1.Identity.GetTenantResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetTenantMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service IdentityService.
   * <pre>
   * IdentityService — internal gRPC API for the Identity microservice.
   * Gateway calls these during JWT validation and authorization.
   * End-user auth flows (login, refresh) are REST/OIDC (not here).
   * Each RPC has its own request and response message, even when the
   * payload would otherwise repeat, so that new fields can be added per RPC
   * without breaking the others.
   * </pre>
   */
  public static abstract class IdentityServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return IdentityServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service IdentityService.
   * <pre>
   * IdentityService — internal gRPC API for the Identity microservice.
   * Gateway calls these during JWT validation and authorization.
   * End-user auth flows (login, refresh) are REST/OIDC (not here).
   * Each RPC has its own request and response message, even when the
   * payload would otherwise repeat, so that new fields can be added per RPC
   * without breaking the others.
   * </pre>
   */
  public static final class IdentityServiceStub
      extends io.grpc.stub.AbstractAsyncStub<IdentityServiceStub> {
    private IdentityServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Validate JWT and resolve to a TenantContext.
     * Gateway calls this on every protected request.
     * </pre>
     */
    public void validateToken(identity.v1.Identity.ValidateTokenRequest request,
        io.grpc.stub.StreamObserver<identity.v1.Identity.ValidateTokenResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getValidateTokenMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Look up user by ID. Used for audit log enrichment.
     * </pre>
     */
    public void getUser(identity.v1.Identity.GetUserRequest request,
        io.grpc.stub.StreamObserver<identity.v1.Identity.GetUserResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetUserMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Look up tenant by ID. Used for tenant quota / config lookups.
     * </pre>
     */
    public void getTenant(identity.v1.Identity.GetTenantRequest request,
        io.grpc.stub.StreamObserver<identity.v1.Identity.GetTenantResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetTenantMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service IdentityService.
   * <pre>
   * IdentityService — internal gRPC API for the Identity microservice.
   * Gateway calls these during JWT validation and authorization.
   * End-user auth flows (login, refresh) are REST/OIDC (not here).
   * Each RPC has its own request and response message, even when the
   * payload would otherwise repeat, so that new fields can be added per RPC
   * without breaking the others.
   * </pre>
   */
  public static final class IdentityServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<IdentityServiceBlockingStub> {
    private IdentityServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Validate JWT and resolve to a TenantContext.
     * Gateway calls this on every protected request.
     * </pre>
     */
    public identity.v1.Identity.ValidateTokenResponse validateToken(identity.v1.Identity.ValidateTokenRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getValidateTokenMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Look up user by ID. Used for audit log enrichment.
     * </pre>
     */
    public identity.v1.Identity.GetUserResponse getUser(identity.v1.Identity.GetUserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetUserMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Look up tenant by ID. Used for tenant quota / config lookups.
     * </pre>
     */
    public identity.v1.Identity.GetTenantResponse getTenant(identity.v1.Identity.GetTenantRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetTenantMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service IdentityService.
   * <pre>
   * IdentityService — internal gRPC API for the Identity microservice.
   * Gateway calls these during JWT validation and authorization.
   * End-user auth flows (login, refresh) are REST/OIDC (not here).
   * Each RPC has its own request and response message, even when the
   * payload would otherwise repeat, so that new fields can be added per RPC
   * without breaking the others.
   * </pre>
   */
  public static final class IdentityServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<IdentityServiceFutureStub> {
    private IdentityServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Validate JWT and resolve to a TenantContext.
     * Gateway calls this on every protected request.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<identity.v1.Identity.ValidateTokenResponse> validateToken(
        identity.v1.Identity.ValidateTokenRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getValidateTokenMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Look up user by ID. Used for audit log enrichment.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<identity.v1.Identity.GetUserResponse> getUser(
        identity.v1.Identity.GetUserRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetUserMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Look up tenant by ID. Used for tenant quota / config lookups.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<identity.v1.Identity.GetTenantResponse> getTenant(
        identity.v1.Identity.GetTenantRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetTenantMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_VALIDATE_TOKEN = 0;
  private static final int METHODID_GET_USER = 1;
  private static final int METHODID_GET_TENANT = 2;

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
        case METHODID_VALIDATE_TOKEN:
          serviceImpl.validateToken((identity.v1.Identity.ValidateTokenRequest) request,
              (io.grpc.stub.StreamObserver<identity.v1.Identity.ValidateTokenResponse>) responseObserver);
          break;
        case METHODID_GET_USER:
          serviceImpl.getUser((identity.v1.Identity.GetUserRequest) request,
              (io.grpc.stub.StreamObserver<identity.v1.Identity.GetUserResponse>) responseObserver);
          break;
        case METHODID_GET_TENANT:
          serviceImpl.getTenant((identity.v1.Identity.GetTenantRequest) request,
              (io.grpc.stub.StreamObserver<identity.v1.Identity.GetTenantResponse>) responseObserver);
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
          getValidateTokenMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              identity.v1.Identity.ValidateTokenRequest,
              identity.v1.Identity.ValidateTokenResponse>(
                service, METHODID_VALIDATE_TOKEN)))
        .addMethod(
          getGetUserMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              identity.v1.Identity.GetUserRequest,
              identity.v1.Identity.GetUserResponse>(
                service, METHODID_GET_USER)))
        .addMethod(
          getGetTenantMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              identity.v1.Identity.GetTenantRequest,
              identity.v1.Identity.GetTenantResponse>(
                service, METHODID_GET_TENANT)))
        .build();
  }

  private static abstract class IdentityServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    IdentityServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return identity.v1.Identity.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("IdentityService");
    }
  }

  private static final class IdentityServiceFileDescriptorSupplier
      extends IdentityServiceBaseDescriptorSupplier {
    IdentityServiceFileDescriptorSupplier() {}
  }

  private static final class IdentityServiceMethodDescriptorSupplier
      extends IdentityServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    IdentityServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (IdentityServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new IdentityServiceFileDescriptorSupplier())
              .addMethod(getValidateTokenMethod())
              .addMethod(getGetUserMethod())
              .addMethod(getGetTenantMethod())
              .build();
        }
      }
    }
    return result;
  }
}
