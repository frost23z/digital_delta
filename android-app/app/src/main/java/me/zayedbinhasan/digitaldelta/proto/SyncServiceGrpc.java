package me.zayedbinhasan.digitaldelta.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.63.0)",
    comments = "Source: sync.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class SyncServiceGrpc {

  private SyncServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "digitaldelta.v1.SyncService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest,
      me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse> getRegisterNodeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterNode",
      requestType = me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest.class,
      responseType = me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest,
      me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse> getRegisterNodeMethod() {
    io.grpc.MethodDescriptor<me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest, me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse> getRegisterNodeMethod;
    if ((getRegisterNodeMethod = SyncServiceGrpc.getRegisterNodeMethod) == null) {
      synchronized (SyncServiceGrpc.class) {
        if ((getRegisterNodeMethod = SyncServiceGrpc.getRegisterNodeMethod) == null) {
          SyncServiceGrpc.getRegisterNodeMethod = getRegisterNodeMethod =
              io.grpc.MethodDescriptor.<me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest, me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterNode"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse.getDefaultInstance()))
              .build();
        }
      }
    }
    return getRegisterNodeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest,
      me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse> getDeltaSyncMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeltaSync",
      requestType = me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest.class,
      responseType = me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest,
      me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse> getDeltaSyncMethod() {
    io.grpc.MethodDescriptor<me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest, me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse> getDeltaSyncMethod;
    if ((getDeltaSyncMethod = SyncServiceGrpc.getDeltaSyncMethod) == null) {
      synchronized (SyncServiceGrpc.class) {
        if ((getDeltaSyncMethod = SyncServiceGrpc.getDeltaSyncMethod) == null) {
          SyncServiceGrpc.getDeltaSyncMethod = getDeltaSyncMethod =
              io.grpc.MethodDescriptor.<me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest, me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeltaSync"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse.getDefaultInstance()))
              .build();
        }
      }
    }
    return getDeltaSyncMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SyncServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SyncServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SyncServiceStub>() {
        @java.lang.Override
        public SyncServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SyncServiceStub(channel, callOptions);
        }
      };
    return SyncServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SyncServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SyncServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SyncServiceBlockingStub>() {
        @java.lang.Override
        public SyncServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SyncServiceBlockingStub(channel, callOptions);
        }
      };
    return SyncServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SyncServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SyncServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SyncServiceFutureStub>() {
        @java.lang.Override
        public SyncServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SyncServiceFutureStub(channel, callOptions);
        }
      };
    return SyncServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void registerNode(me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest request,
        io.grpc.stub.StreamObserver<me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterNodeMethod(), responseObserver);
    }

    /**
     */
    default void deltaSync(me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest request,
        io.grpc.stub.StreamObserver<me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeltaSyncMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service SyncService.
   */
  public static abstract class SyncServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return SyncServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service SyncService.
   */
  public static final class SyncServiceStub
      extends io.grpc.stub.AbstractAsyncStub<SyncServiceStub> {
    private SyncServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SyncServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SyncServiceStub(channel, callOptions);
    }

    /**
     */
    public void registerNode(me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest request,
        io.grpc.stub.StreamObserver<me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterNodeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void deltaSync(me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest request,
        io.grpc.stub.StreamObserver<me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeltaSyncMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service SyncService.
   */
  public static final class SyncServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<SyncServiceBlockingStub> {
    private SyncServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SyncServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SyncServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse registerNode(me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterNodeMethod(), getCallOptions(), request);
    }

    /**
     */
    public me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse deltaSync(me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeltaSyncMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service SyncService.
   */
  public static final class SyncServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<SyncServiceFutureStub> {
    private SyncServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SyncServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SyncServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse> registerNode(
        me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterNodeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse> deltaSync(
        me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeltaSyncMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_NODE = 0;
  private static final int METHODID_DELTA_SYNC = 1;

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
        case METHODID_REGISTER_NODE:
          serviceImpl.registerNode((me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest) request,
              (io.grpc.stub.StreamObserver<me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse>) responseObserver);
          break;
        case METHODID_DELTA_SYNC:
          serviceImpl.deltaSync((me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest) request,
              (io.grpc.stub.StreamObserver<me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse>) responseObserver);
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
          getRegisterNodeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              me.zayedbinhasan.digitaldelta.proto.RegisterNodeRequest,
              me.zayedbinhasan.digitaldelta.proto.RegisterNodeResponse>(
                service, METHODID_REGISTER_NODE)))
        .addMethod(
          getDeltaSyncMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest,
              me.zayedbinhasan.digitaldelta.proto.DeltaSyncResponse>(
                service, METHODID_DELTA_SYNC)))
        .build();
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (SyncServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .addMethod(getRegisterNodeMethod())
              .addMethod(getDeltaSyncMethod())
              .build();
        }
      }
    }
    return result;
  }
}
