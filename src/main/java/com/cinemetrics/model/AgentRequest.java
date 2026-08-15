package com.cinemetrics.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AgentRequest {

    @NotBlank(message = "Query cannot be blank")
    @Size(max = 1000, message = "Query must be under 1000 characters")
    private String query;

    private String[] filmIds;

    public String getQuery()          { return query; }
    public String[] getFilmIds()      { return filmIds; }
    public void setQuery(String v)    { this.query = v; }
    public void setFilmIds(String[] v){ this.filmIds = v; }
}
