package com.gleeds.quiz.game;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * What a Player gives to join any Game (Solo start, Battle join). The email only tells Players apart and never
 * leaves the server (docs/adr/0003); an old client's {@code consent} is an unknown field, so it is ignored.
 */
record JoinRequest(@NotBlank String name, @NotBlank @Email String email) {
}
