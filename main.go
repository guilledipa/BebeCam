package main

import (
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
)

func main() {
	// Proxy WebRTC WHEP negotiation directly to MediaMTX
	target, _ := url.Parse("http://127.0.0.1:8889")
	proxy := httputil.NewSingleHostReverseProxy(target)
	http.Handle("/bebe/whep", proxy)

	fs := http.FileServer(http.Dir("./web"))
	http.Handle("/", fs)

	port := "8080"
	addr := "0.0.0.0:" + port

	l, err := net.Listen("tcp4", addr)
	if err != nil {
		log.Fatalf("Failed to create IPv4 listener: %v", err)
	}

	log.Printf("BebeCam Dashboard & WHEP Proxy starting on %s (forced IPv4)\n", addr)
	err = http.Serve(l, nil)
	if err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
