package com.example.screenservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "screens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screen_id")
    private Integer screenId;

    @Column(name = "theater_id", nullable = false)
    private Integer theaterId;

    @Column(nullable = false)
    private String name;

    @Column(name = "screen_type")
    private String screenType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}