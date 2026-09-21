package com.gleeds.quiz.game;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * What a Player gives to join any Game (Solo start, Battle join): the Lead and consent. {@code consent} is a primitive
 * so a missing field reads as false and fails {@link AssertTrue}.
 */
record JoinRequest(@NotBlank String name, @NotBlank @Email String email, @AssertTrue boolean consent) {
}
