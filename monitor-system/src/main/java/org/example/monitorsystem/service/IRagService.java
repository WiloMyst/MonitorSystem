package org.example.monitorsystem.service;

import reactor.core.publisher.Flux;

public interface IRagService {
    Flux<String> smartChat(String question);
}
