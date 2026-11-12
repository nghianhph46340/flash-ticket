package com.ticket.infrastructure.adapter.in.web;

import com.ticket.core.service.CinemaService;
import com.ticket.infrastructure.adapter.in.web.dto.ConcessionResponse;
import com.ticket.infrastructure.adapter.in.web.dto.MovieResponse;
import com.ticket.infrastructure.adapter.in.web.dto.ShowtimeResponse;
import com.ticket.infrastructure.adapter.out.persistence.entity.AuditoriumEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.ShowtimeEntity;
import com.ticket.infrastructure.adapter.out.persistence.repository.AuditoriumRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;
    private final AuditoriumRepository auditoriumRepository;

    /** Danh sách phim đang chiếu */
    @GetMapping("/movies")
    public ResponseEntity<List<MovieResponse>> getMovies() {
        List<MovieResponse> movies = cinemaService.getActiveMovies()
                .stream().map(MovieResponse::from).toList();
        return ResponseEntity.ok(movies);
    }

    /** Chi tiết 1 phim */
    @GetMapping("/movies/{movieId}")
    public ResponseEntity<MovieResponse> getMovie(@PathVariable UUID movieId) {
        return ResponseEntity.ok(MovieResponse.from(cinemaService.getMovieById(movieId)));
    }

    /** Danh sách suất chiếu của 1 phim */
    @GetMapping("/movies/{movieId}/showtimes")
    public ResponseEntity<List<ShowtimeResponse>> getShowtimes(@PathVariable UUID movieId) {
        List<ShowtimeEntity> showtimes = cinemaService.getShowtimesForMovie(movieId);

        // Lấy auditorium info để enrich response
        Map<UUID, AuditoriumEntity> auditoriumMap = auditoriumRepository
                .findAllById(showtimes.stream().map(ShowtimeEntity::getAuditoriumId).distinct().toList())
                .stream().collect(Collectors.toMap(AuditoriumEntity::getId, Function.identity()));

        List<ShowtimeResponse> responses = showtimes.stream()
                .map(s -> ShowtimeResponse.from(s, auditoriumMap.get(s.getAuditoriumId())))
                .toList();
        return ResponseEntity.ok(responses);
    }

    /** Chi tiết 1 suất chiếu */
    @GetMapping("/showtimes/{showtimeId}")
    public ResponseEntity<ShowtimeResponse> getShowtime(@PathVariable UUID showtimeId) {
        ShowtimeEntity showtime = cinemaService.getShowtimeById(showtimeId);
        AuditoriumEntity auditorium = auditoriumRepository.findById(showtime.getAuditoriumId()).orElse(null);
        return ResponseEntity.ok(ShowtimeResponse.from(showtime, auditorium));
    }

    /** Danh sách bắp nước F&B */
    @GetMapping("/concessions")
    public ResponseEntity<List<ConcessionResponse>> getConcessions() {
        List<ConcessionResponse> list = cinemaService.getActiveConcessions()
                .stream().map(ConcessionResponse::from).toList();
        return ResponseEntity.ok(list);
    }
}
