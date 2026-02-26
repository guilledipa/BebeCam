package main

import (
	"log"
	"net"
	"net/http"
)

func main() {
	fs := http.FileServer(http.Dir("./web"))
	http.Handle("/", fs)

	port := "8080"
	addr := "0.0.0.0:" + port

	// Explicitly create a TCP4 listener to force IPv4-only binding
	l, err := net.Listen("tcp4", addr)
	if err != nil {
		log.Fatalf("Failed to create IPv4 listener: %v", err)
	}

	log.Printf("BebeCam Dashboard starting on %s (forced IPv4)\n", addr)
	err = http.Serve(l, nil)
	if err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
