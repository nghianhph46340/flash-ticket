package com.ticket.infrastructure.adapter.in.web.dto;

import com.ticket.infrastructure.adapter.out.persistence.entity.MovieEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class MovieResponse {
    private UUID id;
    private String title;
    private String slug;
    private int durationMinutes;
    private String ageRating;
    private String director;
    private String synopsis;
    private String posterUrl;
    private String trailerUrl;
    private LocalDate releaseDate;

    public static MovieResponse from(MovieEntity entity) {
        return MovieResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .slug(entity.getSlug())
                .durationMinutes(entity.getDurationMinutes())
                .ageRating(entity.getAgeRating().name())
                .director(entity.getDirector())
                .synopsis(entity.getSynopsis())
                .posterUrl(entity.getPosterUrl())
                .trailerUrl(entity.getTrailerUrl())
                .releaseDate(entity.getReleaseDate())
                .build();
    }
}
