package com.cinemetrics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public class Film {

    @JsonProperty("film_id")
    private String filmId;

    private String title;

    @JsonProperty("budget_usd")
    private Long budgetUsd;

    @JsonProperty("release_date")
    private LocalDate releaseDate;

    @JsonProperty("mpaa_rating")
    private String mpaaRating;

    private String genre;
    private String director;
}
