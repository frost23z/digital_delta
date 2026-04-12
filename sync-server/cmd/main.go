package main

import (
	"context"
	"errors"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	digitaldeltav1 "github.com/frost23z/digital_delta/sync-server/internal/gen/proto"
	"github.com/frost23z/digital_delta/sync-server/internal/handlers"
	"github.com/frost23z/digital_delta/sync-server/internal/httpapi"
	"github.com/frost23z/digital_delta/sync-server/internal/security"
	"github.com/frost23z/digital_delta/sync-server/internal/storage"
	"google.golang.org/grpc"
)

func main() {
	addr := os.Getenv("SYNC_SERVER_ADDR")
	if addr == "" {
		addr = ":50051"
	}
	httpAddr := os.Getenv("SYNC_HTTP_ADDR")
	if httpAddr == "" {
		httpAddr = ":8081"
	}

	dbPath := os.Getenv("SYNC_DB_PATH")
	if dbPath == "" {
		dbPath = "./digital_delta_sync.db"
	}

	db, err := storage.OpenSQLite(dbPath)
	if err != nil {
		log.Fatalf("failed to initialize sqlite at %s: %v", dbPath, err)
	}
	defer db.Close()

	listener, err := net.Listen("tcp", addr)
	if err != nil {
		log.Fatalf("failed to listen on %s: %v", addr, err)
	}

	mutationStore := storage.NewMutationStore(db)
	checkpointStore := storage.NewCheckpointStore(db)
	verifier := security.NoopSignatureVerifier{}
	syncHandler := handlers.NewSyncHandler(mutationStore, checkpointStore, verifier)

	grpcServer := grpc.NewServer()
	digitaldeltav1.RegisterSyncServiceServer(
		grpcServer,
		syncHandler,
	)

	mux := http.NewServeMux()
	httpapi.RegisterRoutes(mux, syncHandler)
	httpServer := &http.Server{
		Addr:    httpAddr,
		Handler: mux,
	}

	go func() {
		log.Printf("sync server listening on %s (sqlite: %s)", addr, dbPath)
		if serveErr := grpcServer.Serve(listener); serveErr != nil {
			log.Fatalf("gRPC server stopped with error: %v", serveErr)
		}
	}()

	go func() {
		log.Printf("sync HTTP API listening on %s", httpAddr)
		if serveErr := httpServer.ListenAndServe(); serveErr != nil && !errors.Is(serveErr, http.ErrServerClosed) {
			log.Fatalf("HTTP server stopped with error: %v", serveErr)
		}
	}()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	log.Println("shutting down sync server")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		log.Printf("HTTP shutdown error: %v", err)
	}
	grpcServer.GracefulStop()
}
