package com.ticket.core.service;

import com.ticket.infrastructure.adapter.out.persistence.entity.*;
import com.ticket.infrastructure.adapter.out.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CinemaService {

    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ConcessionRepository concessionRepository;

    @Transactional(readOnly = true)
    public List<MovieEntity> getActiveMovies() {
        return movieRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public MovieEntity getMovieById(UUID id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ShowtimeEntity> getShowtimesForMovie(UUID movieId) {
        return showtimeRepository.findByMovieIdAndStatusOrderByStartTimeAsc(
                movieId, ShowtimeEntity.ShowtimeStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public ShowtimeEntity getShowtimeById(UUID id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CinemaEntity> getActiveCinemas() {
        return cinemaRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<AuditoriumEntity> getAuditoriumsByCinema(UUID cinemaId) {
        return auditoriumRepository.findByCinemaIdAndActiveTrue(cinemaId);
    }

    @Transactional(readOnly = true)
    public List<ConcessionEntity> getActiveConcessions() {
        return concessionRepository.findByActiveTrue();
    }

    @Transactional
    public ShowtimeEntity createShowtime(ShowtimeEntity showtime) {
        // Kiểm tra chống trùng lịch phòng chiếu (overlap prevention)
        boolean hasOverlap = showtimeRepository.existsOverlappingShowtime(
                showtime.getAuditoriumId(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                null
        );
        if (hasOverlap) {
            throw new IllegalStateException(
                    "Phòng chiếu đã có suất chiếu khác trong khung giờ này. Vui lòng chọn thời gian khác.");
        }
        return showtimeRepository.save(showtime);
    }
}
