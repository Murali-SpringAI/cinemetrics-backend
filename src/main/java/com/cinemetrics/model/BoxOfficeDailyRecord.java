package com.cinemetrics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public class BoxOfficeDailyRecord {

    @JsonProperty("film_id")
    private String filmId;

    private LocalDate date;
    private String region;

    @JsonProperty("gross_usd")
    private Long grossUsd;

    @JsonProperty("theatre_count")
    private Integer theatreCount;

    @JsonProperty("week_number")
    private Integer weekNumber;
}
