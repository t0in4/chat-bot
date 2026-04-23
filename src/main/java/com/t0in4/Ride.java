package com.t0in4;

import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;

@Entity
public class Ride {
    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public String name;
    public double rating;

}
