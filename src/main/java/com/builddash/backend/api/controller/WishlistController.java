package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.api.dto.request.AddWishlistItemRequest;
import com.builddash.backend.api.dto.response.WishlistEntryResponse;
import com.builddash.backend.api.mapper.WishlistMapper;
import com.builddash.backend.application.service.WishlistReader;
import com.builddash.backend.application.service.WishlistWriter;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Wishlist", description = "Save products for later")
@SecurityRequirement(name = "bearerAuth")
public class WishlistController {

    private final WishlistReader wishlistReader;
    private final WishlistWriter wishlistWriter;
    private final WishlistMapper wishlistMapper;

    public WishlistController(WishlistReader wishlistReader, WishlistWriter wishlistWriter, WishlistMapper wishlistMapper) {
        this.wishlistReader = wishlistReader;
        this.wishlistWriter = wishlistWriter;
        this.wishlistMapper = wishlistMapper;
    }

    @GetMapping("/users/me/wishlist")
    @Operation(summary = "Get my wishlist")
    public List<WishlistEntryResponse> getWishlist(@AuthenticationPrincipal AuthenticatedUser principal) {
        return wishlistMapper.toResponseList(wishlistReader.list(principal.userId()));
    }

    @PostMapping("/users/me/wishlist")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a product to my wishlist", description = "Idempotent — adding an already-wishlisted product returns the existing entry.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entry created (or already existed)"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public WishlistEntryResponse addToWishlist(@Valid @RequestBody AddWishlistItemRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        return wishlistMapper.toResponse(wishlistWriter.add(principal.userId(), request.productId()));
    }

    @DeleteMapping("/users/me/wishlist/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a product from my wishlist")
    public void removeFromWishlist(@PathVariable UUID productId, @AuthenticationPrincipal AuthenticatedUser principal) {
        wishlistWriter.remove(principal.userId(), productId);
    }
}
