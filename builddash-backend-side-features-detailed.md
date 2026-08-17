# BuildDash Customer — Backend Side Features (Detailed)

Every API entry below follows this format so an implementer (human or AI) knows exactly what to build, not just how:

> **Endpoint** — `METHOD /path`
> **Purpose:** the job this API does, in plain terms — why it exists, what problem it solves
> **Behavior/logic:** what it must actually do internally
> **Data touched:** key tables/fields
> **Edge cases:** things that break if skipped

---

## 1. Account & Identity

**Send OTP**
`POST /auth/otp/send`
**Purpose:** Start the login/signup process by proving the user controls this phone number.
**Behavior/logic:** Generate a 6-digit OTP, hash it, store in **Redis** with key `otp:{phone}` and a 5-minute TTL (do NOT store OTP in the primary DB — it's short-lived, high-write, and Redis's native expiry removes the need for a cleanup job). Dispatch via SMS gateway.
**Data touched:** Redis only at this step (no user record created yet).
**Edge cases:** Rate-limit per phone (e.g. 5 sends/hour) using a separate Redis counter key; block rapid re-sends within the TTL window.

**Verify OTP**
`POST /auth/otp/verify`
**Purpose:** Confirm the OTP and issue a session, creating the user account if this is their first time.
**Behavior/logic:** Look up `otp:{phone}` in Redis, compare hash, delete the key on success (single-use). If no user record exists for this phone, create one. Issue access JWT (short-lived) + refresh JWT (long-lived, stored hashed in DB tied to a device record).
**Data touched:** `users` table (create/fetch), `sessions`/`devices` table (new row).
**Edge cases:** Wrong OTP → increment a failure counter in Redis (`otp_fail:{phone}`), lock out after N attempts even before TTL expires (prevents brute force within the 5-min window).

**Google Sign-In**
`POST /auth/google`
**Purpose:** Alternate login path — let a user sign in via their Google account instead of OTP.
**Behavior/logic:** Verify the ID token server-side against Google's public keys (never trust a client-asserted email). Match to existing user by phone/email if present, else create new account.
**Data touched:** `users` table.
**Edge cases:** Token replay — always re-verify server-side per request, never cache "trusted" status client-side.

**Guest session**
`POST /auth/guest`
**Purpose:** Let a user browse and build a cart without creating an account, to reduce signup friction.
**Behavior/logic:** Issue a scoped token that allows read + cart-mutation endpoints but is rejected by order-placement endpoints.
**Data touched:** Temporary session record, guest cart keyed by session ID.
**Edge cases:** On real signup, merge the guest cart/wishlist into the new account by session ID, then invalidate the guest token.

**Profile**
`GET /users/me`, `PUT /users/me`
**Purpose:** Read and update the user's own profile info (name, business name, GST number).
**Behavior/logic:** `PUT` validates GST number format, then triggers async GSTIN verification (see below) — don't block the response on that external call.
**Data touched:** `users` table.
**Edge cases:** GSTIN validation failing shouldn't block profile save — store `gstin_status: pending/verified/invalid` separately and update it once the async check completes.

**Session/device management**
`GET /users/me/devices`
**Purpose:** Let the user see every device currently logged into their account, for security awareness.
**Behavior/logic:** List all active refresh-token records tied to the user, with device fingerprint/last-seen metadata.
**Data touched:** `devices`/`sessions` table.

`DELETE /users/me/devices/{id}`
**Purpose:** Revoke one specific device's session (e.g. "I lost my old phone").
**Behavior/logic:** Delete/invalidate that device's refresh token so it can no longer mint new access tokens.
**Data touched:** `devices` table.

`POST /users/me/logout-all-devices`
**Purpose:** Emergency "kick everyone out" action for a compromised account — invalidates every active session at once, not just the current one.
**Behavior/logic:** Delete/invalidate ALL refresh tokens tied to the user in one transaction, forcing every device (including the one making this request, if desired — usually the current device gets a fresh token issued right after) to re-authenticate via OTP.
**Data touched:** `devices`/`sessions` table (bulk delete/invalidate by `user_id`).
**Edge cases:** Decide explicitly whether the requesting device stays logged in or also gets logged out — most apps re-issue a fresh session for the current device immediately after, so the user isn't locked out of the action they just took.

**Number change**
`POST /users/me/phone/change`
**Purpose:** Let a user update their registered phone number without losing their account.
**Behavior/logic:** Requires OTP verified on BOTH old and new numbers before committing — run as one DB transaction so a partial change is never possible.
**Data touched:** `users.phone`.

**Email**
`POST /users/me/email`, `POST /users/me/email/verify`
**Purpose:** Add a recovery/notification email and confirm the user actually owns it.
**Behavior/logic:** Generate a verification token, email a link, mark verified on click.
**Data touched:** `users.email`, `users.email_verified`.

**Password reset** (if alternate auth used)
`POST /auth/password/reset-request`, `POST /auth/password/reset`
**Purpose:** Recover account access without the phone, via email/SMS-delivered reset link.
**Behavior/logic:** Time-limited, single-use reset token (store in Redis with TTL, same pattern as OTP).

**2FA**
`POST /users/me/2fa/enable`, `POST /users/me/2fa/verify`
**Purpose:** Add a second verification factor for high-value B2B accounts, so a stolen password/OTP alone isn't enough.
**Behavior/logic:** Generate TOTP secret, verify a code before enabling; require the 2FA code on login once enabled.
**Data touched:** `users.totp_secret` (encrypted at rest).

**Login history**
`GET /users/me/login-history`
**Purpose:** Let the user audit when/where their account was accessed.
**Behavior/logic:** Return timestamped log of login events with IP + device fingerprint.
**Data touched:** `login_events` table (append-only, written by the OTP-verify/Google-signin endpoints).

**Privacy consent**
`GET /users/me/consent`, `PUT /users/me/consent`
**Purpose:** Let the user control what their data is used for (marketing vs operational-only), per DPDP requirements.
**Data touched:** `users.consent_flags`.

**Account deletion**
`POST /users/me/delete-request`
**Purpose:** Start the DPDP-mandated account deletion process, with a grace period so accidental/coerced requests can be undone.
**Behavior/logic:** Set `deletion_scheduled_at = now + 30 days`. A scheduled job later purges PII fields but retains GST/financial records per legal retention rules.
**Data touched:** `users.deletion_scheduled_at`.

`POST /users/me/delete-request/cancel`
**Purpose:** Let the user back out of a pending deletion within the grace period.
**Behavior/logic:** Clear `deletion_scheduled_at`.

---

## 2. Discovery & Search

**Search**
`GET /search?q=&lang=hi|en`
**Purpose:** Find matching products even when the query is misspelled, partial, or in Hindi.
**Behavior/logic:** Fuzzy match (trigram/edit-distance) plus exact-match boosting; look up against a Hindi↔English alias table for transliterated product names, not just raw Unicode matching.
**Data touched:** Search index (Elasticsearch/OpenSearch or DB full-text index), product alias table.

**Autocomplete**
`GET /search/suggest?q=`
**Purpose:** Show live suggestions as the user types, so they don't have to finish typing or know exact product names.
**Behavior/logic:** Prefix + fuzzy suggestions, cache hot queries in Redis since this fires on nearly every keystroke.

**Search history**
`GET /users/me/search-history`, `DELETE /users/me/search-history`
**Purpose:** Let the user quickly re-run recent searches and clear that history for privacy.
**Data touched:** `search_history` table, capped at last N entries per user.

**Image search**
`POST /search/image`
**Purpose:** Let a user find a product by photographing it or its packaging, useful on a construction site where typing is inconvenient.
**Behavior/logic:** Accept image upload, run through a vision/embedding model or third-party product-matching API, return ranked SKU matches.

**Trending searches**
`GET /search/trending`
**Purpose:** Surface what's popular right now so users without a specific query still get useful starting points.
**Behavior/logic:** Served from a cached table populated by a background job aggregating last-24h query volume by region.

---

## 3. Catalog (read-side)

**Listing**
`GET /products?category=&brand=&price_min=&price_max=&sort=`
**Purpose:** Power the browse/category screens — return a filtered, paginated set of products.
**Behavior/logic:** Cursor-based pagination (not offset) since the catalog is large and changes frequently.

**Detail**
`GET /products/{id}`
**Purpose:** Return everything needed to render a single product page in one call.
**Behavior/logic:** Includes specs, images, brand info, and real-time aggregated stock across all warehouses.
**Edge cases:** Cache with a short TTL — stock changes fast enough that a stale "in stock" response leads directly to failed orders.

**Back-in-stock subscription**
`POST /products/{id}/notify-me`
**Purpose:** Let a user ask to be told when an out-of-stock item becomes available again.
**Behavior/logic:** Store subscription; an inventory-update event (from the vendor-side restock action) triggers a job that checks subscribers and fires notifications, then clears them.

**Reviews**
`POST /products/{id}/reviews`
**Purpose:** Let customers leave feedback that helps other buyers decide.
**Behavior/logic:** Requires a verified-purchase check (join against completed orders for that SKU + user) before allowing submission.

**Q&A**
`POST /products/{id}/questions`, `POST /questions/{id}/answers`
**Purpose:** Let customers ask product questions and get answers from vendors, BuildDash staff, or other customers.
**Data touched:** `answer.source` field distinguishes who answered.

**Wishlist**
`GET/POST/DELETE /users/me/wishlist`
**Purpose:** Let users save products for later without adding them to an active cart.

**Recommendations**
`GET /products/{id}/related`, `GET /users/me/recommendations`
**Purpose:** Surface relevant products to increase basket size and help discovery.
**Behavior/logic:** Cold-start users (no order history) get content-based suggestions from browsing/category affinity; users with history get collaborative-filtering results from a precomputed nightly batch job — do NOT compute collaborative filtering live per-request, it's too expensive.

---

## 4. Pricing & Tax

**Pricing calculation**
`POST /pricing/calculate` (internal service, called by cart/checkout)
**Purpose:** The single source of truth for "what does this cart cost right now" — every price shown to the customer must come from here, never from a client-cached number.
**Behavior/logic:** Resolves in strict order: base price → bulk-quantity tier → contract-price override (B2B) → margin-floor check. This precedence must be deterministic and unit-tested per rule combination — it's the most bug-prone module in the whole backend.
**Data touched:** `pricing_tiers`, `contract_pricing`, `margin_rules` tables.

**Tax (GST) resolution — how the "shopkeeper enters tax" question actually resolves**
`(internal — part of the pricing calculation, not a separate customer/vendor-facing endpoint)`
**Purpose:** Apply the legally correct GST rate to every line item automatically, with zero manual tax entry by the vendor.
**Behavior/logic:**
1. Every SKU is tagged with an **HSN code** at catalog-creation time (vendor picks from a lookup list, doesn't free-type a rate).
2. Admin maintains a master `hsn_gst_rates` table (HSN code → GST %), updated only when government slabs change — this is platform master data, not per-vendor or per-order data.
3. At calculation time, the pricing engine looks up `hsn_gst_rates[sku.hsn_code]` and applies it to the price breakup.
4. BuildDash (not the vendor) is the invoicing entity — the GSTIN printed on the customer invoice is BuildDash's.
**Data touched:** `products.hsn_code`, `hsn_gst_rates` (admin-managed master table).
**Edge cases:** A product mis-tagged with the wrong HSN code silently overcharges/undercharges tax — flag this as a catalog-completeness check (see catalog moderation), not something to catch at checkout time.

**Customer GSTIN capture (separate from tax rate)**
`PUT /users/me` (GST field) or `POST /orders/{id}/gstin`
**Purpose:** Let a B2B customer attach their own GSTIN to an order so they can claim input tax credit — this does NOT change the rate charged, only what's printed on the invoice for their records.
**Behavior/logic:** Validate format, optionally verify against GST portal API asynchronously, store separately from the order's tax calculation.

**E-way bill trigger**
`(internal job, triggered on order confirmation)`
**Purpose:** Auto-generate the government-mandated e-way bill for any order above ₹50,000, since this is a legal requirement tied to the same HSN/tax data.
**Behavior/logic:** Reads the same HSN/GST data used in pricing, calls the e-way bill generation service, stores the generated bill reference against the order.

**Coupons**
`POST /cart/apply-coupon`
**Purpose:** Validate and apply a discount code to the current cart.
**Behavior/logic:** Checks expiry, per-user usage count, minimum order value, eligible categories, and stacking rules against other active discounts — reject with a specific error code (not a generic failure) so the app can show a helpful message.

**Margin floor**
`(internal, part of pricing calculation)`
**Purpose:** Guarantee BuildDash never sells below a configured minimum margin, even after coupons/discounts stack.
**Behavior/logic:** Runs after coupon application, before payment. If breached: either auto-adjust the discount down or block checkout and flag for manual review.

---

## 5. Cart & Checkout

**Cart**
`GET /cart`, `PUT /cart`
**Purpose:** Server-authoritative cart state — the client never trusts its own cached prices/totals.
**Behavior/logic:** Every mutation re-runs the pricing calculation (Section 4) before returning the updated cart.
**Data touched:** `cart_items` table, scoped by `(user_id, project_id | null)` for B2B multi-cart support.

**Addresses**
`POST /addresses`, `GET /addresses`, `PUT/DELETE /addresses/{id}`
**Purpose:** Let a customer save and manage delivery locations (Home, Site 1, Site 2, etc.).
**Behavior/logic:** Store lat/lng from the map pin, reverse-geocode via Google Maps for a human-readable display address. Validate the address falls within at least one warehouse's service radius before allowing it as a delivery destination — otherwise the customer picks an address they can't actually get delivery to.

**Delivery slots**
`GET /delivery-slots?address_id=`
**Purpose:** Show only genuinely available delivery windows for this address, based on warehouse capacity.
**Behavior/logic:** Computed from warehouse operating hours + current booked capacity. Lock a slot with a short TTL on selection to prevent two customers overbooking the same slot; release the lock if checkout is abandoned.

**Pre-payment re-validation**
`(internal, runs immediately before payment initiation)`
**Purpose:** Catch anything that changed between "added to cart" and "about to pay" — stock gone, price changed, slot expired — before money moves.
**Behavior/logic:** Re-check stock availability, re-run pricing, re-check slot validity; reject with a specific reason if any check fails so the app can show exactly what changed.

**Cart abandonment**
`(scheduled job)`
**Purpose:** Recover potentially lost sales by reminding customers who left items in cart.
**Behavior/logic:** Carts untouched for 1 hour with items → publish an event to the notification service (Section 15) — this job doesn't send the notification itself, it just triggers it.

---

## 6. Payments

**Initiate payment**
`POST /payments/initiate`
**Purpose:** Start a PhonePe transaction for the current order.
**Behavior/logic:** Create a PhonePe order via their API, return the redirect/SDK payload the app needs to open the payment flow.

**Payment webhook**
`POST /payments/webhook`
**Purpose:** Receive PhonePe's authoritative confirmation of payment success/failure — this, not the client, is what actually confirms an order.
**Behavior/logic:** Verify the webhook signature, process idempotently (webhooks can be delivered more than once — dedupe by transaction ID). Order only transitions to `confirmed` after this webhook succeeds, never on client-reported success alone.

**Payment retry**
`POST /payments/retry`
**Purpose:** Let a customer try a different payment method after a failure, without losing their order/cart.
**Behavior/logic:** Order stays in `payment_pending` state on failure; retry re-attempts against the same order rather than creating a new one.

**BNPL eligibility**
`GET /payments/bnpl/eligibility?amount=`
**Purpose:** Show only the Buy-Now-Pay-Later options this specific customer actually qualifies for.
**Behavior/logic:** Calls each configured BNPL provider's eligibility API, returns the subset that approves.

**BNPL settlement reconciliation**
`(scheduled job)`
**Purpose:** Confirm that BNPL providers (who pay BuildDash upfront) actually settle as expected.
**Behavior/logic:** Nightly job matches BuildDash's payout records against each provider's settlement report, flags mismatches.

---

## 7. Order Management

**Order state transitions**
`PUT /orders/{id}/status` (internal, called by warehouse/delivery-partner webhooks, not directly by the customer app)
**Purpose:** Enforce that an order only moves through valid states in valid order.
**Behavior/logic:** Reject invalid jumps (e.g. `Confirmed → Delivered` skipping `Dispatched`) at the API layer, not just in UI — this is a data-integrity guarantee, not a UX nicety.

**Order history**
`GET /orders`, `GET /orders/{id}`
**Purpose:** Let the customer view past and current orders.

**Reorder**
`POST /orders/{id}/reorder`
**Purpose:** Let a customer quickly rebuild their cart from a past order instead of re-searching everything.
**Behavior/logic:** Copies line items into a new cart, then re-runs pricing (prices may have changed since the original order).

**Delivery tracking ingestion**
`POST /internal/delivery-webhook`
**Purpose:** Receive live location/status updates from the delivery partner and make them available to the customer app in real time.
**Behavior/logic:** Update the order's current location/status, broadcast via WebSocket to the subscribed customer app instance.

**Tracking (client-facing)**
`GET /orders/{id}/tracking`
**Purpose:** Fallback for when the WebSocket connection drops — lets the app poll for the latest state.

**Masked driver call**
`POST /orders/{id}/call-driver`
**Purpose:** Let the customer contact the driver without either party seeing the other's real phone number.
**Behavior/logic:** Request a temporary proxy-number pairing from the masking provider (Exotel/Knowlarity); auto-expire the pairing once delivery completes.

**Order modification**
`PUT /orders/{id}`
**Purpose:** Let a customer change delivery slot/address/cancel within an allowed window.
**Behavior/logic:** Server checks `now < order.placed_at + modification_window` before allowing any change — this must be enforced here, not just hidden in the UI, since the UI check alone can be bypassed.

---

## 8. Returns, Refunds & Replacements

**Return request**
`POST /orders/{id}/return`
**Purpose:** Let a customer initiate a return for all or part of an order.
**Behavior/logic:** Accepts reason (enum), photos (stored in object storage), and supports partial-quantity returns at the line-item level, not just whole-order returns.

**Return status**
`GET /returns/{id}`
**Purpose:** Let the customer track where their return is in the process.
**Behavior/logic:** State machine: `Requested → Approved → PickupScheduled → PickedUp → QC → RefundInitiated → RefundCompleted`.

**Refund processing**
`(internal, triggered on QC pass)`
**Purpose:** Actually move money back to the customer once a return is approved.
**Behavior/logic:** Calls the payment gateway's refund API; computed expected-completion date shown to customer is an estimate by payment method (UPI ~24h, cards 5-7 business days), not a guarantee.

**GST credit/debit notes**
`(internal, auto-generated on return/refund)`
**Purpose:** Issue the tax documents legally required alongside a refund or additional charge.
**Behavior/logic:** Must follow GSTR-1 sequential numbering with no gaps — this is a compliance requirement, get the numbering logic right from day one since retrofitting sequential numbering later is painful.

---

## 9. Invoicing

`GET /orders/{id}/invoice`
**Purpose:** Give the customer a GST-compliant document for their records/accounting.
**Behavior/logic:** Generate the PDF (HSN codes, GSTIN, tax breakdown) once, at order confirmation — store in object storage and serve via signed URL on request rather than regenerating every time.

---

## 10. B2B Module

**Company roles**
`(enforced at API middleware level across all B2B endpoints)`
**Purpose:** Ensure a user can only do what their role/site-assignment permits within their company account.
**Behavior/logic:** `members` table stores `(user_id, company_id, role, assigned_sites[])`; every B2B endpoint checks both role AND site scope, not just "is this user logged in."

**RFQ**
`POST /rfq`, `GET /rfq/{id}/quotes`
**Purpose:** Let a customer request formal quotes from vendors before committing to an order.
**Behavior/logic:** Routes the RFQ to matching vendors by category/capability; vendors respond via a vendor-side endpoint; a scheduled job auto-expires quotes past their validity window.

**Purchase Orders**
`POST /orders/{id}/po`, `POST /po/bulk`
**Purpose:** Let B2B customers attach their internal PO documentation to an order, and upload many at once.
**Behavior/logic:** Bulk upload parses an Excel file, validates each row, creates draft orders for review.

**Approval workflow**
`(internal rule engine, triggered on order placement)`
**Purpose:** Enforce that large/sensitive B2B orders get sign-off from the right person before proceeding.
**Behavior/logic:** Evaluates order amount/category/site against configured thresholds, creates a `pending_approval` record assigned to the right approver; a scheduled job auto-escalates to the next role if not actioned within the configured hours.

**Monthly statements**
`(scheduled job, 1st of every month)`
**Purpose:** Give B2B customers one consolidated document instead of hunting through per-order invoices.
**Behavior/logic:** Aggregates all of a company's orders for the month, generates PDF + Excel, emails to the designated accountant address. Per-site/per-project breakdown requires orders to already be tagged at creation time — this is much harder to retrofit later, so get the tagging in from the start.

---

## 11. Reliability, Offline & Performance

**Idempotent order/payment creation**
`(header: Idempotency-Key on POST /orders, POST /payments/initiate)`
**Purpose:** Make it safe for the app to retry a request after a network timeout without accidentally creating duplicate orders or charges.
**Behavior/logic:** Server dedupes by the idempotency key for a rolling window (e.g. 24h) — a retried request with the same key returns the original result instead of creating a new one.

---

## 12. Customer Support

**Tickets**
`POST /support/tickets`, `POST /support/tickets/{id}/messages`
**Purpose:** Let a customer raise and track a support issue.
**Behavior/logic:** Categorization enum drives SLA timers per category.

**WhatsApp integration**
`(internal — Meta WhatsApp Business API)`
**Purpose:** Deliver order confirmations, dispatch alerts, and invoices over the channel Indian customers actually check.
**Behavior/logic:** Uses pre-approved template messages (Meta approval takes days — submit templates early in the project, don't leave this for the end).

**Chatbot**
`(internal NLU service, called from chat/WhatsApp entry points)`
**Purpose:** Auto-resolve common queries without a human agent.
**Behavior/logic:** Intent classification against ~20-30 known query types; below a confidence threshold, auto-escalate to a human agent, passing full conversation context along.

---

## 13. Engagement & Loyalty

**Referrals**
`POST /referrals/generate`, `POST /referrals/redeem`
**Purpose:** Reward existing customers for bringing in new ones.
**Behavior/logic:** Credit is only applied after the referred user's first *qualifying* order (not just signup) — flag same-device-ID or same-address referrals for manual review before crediting, to prevent self-referral abuse.

---

## 14. Security & Compliance

**PII isolation**
`(architectural, not a single endpoint)`
**Purpose:** Limit the blast radius if any single service/token is compromised.
**Behavior/logic:** Name/phone/address fields encrypted at rest, stored in a schema/table separate from order/transaction data.

**DPDP data export**
`GET /users/me/export`
**Purpose:** Fulfil the legal right for a user to receive all their data in a portable format.
**Behavior/logic:** Returns machine-readable JSON/CSV of everything held about the user.

---

## 15. Notifications

**Internal notify service**
`POST /internal/notify`
**Purpose:** Give every other part of the backend one single way to send a customer a message, without each of them needing to know about push/SMS/WhatsApp separately.
**Behavior/logic:** Abstracts push (FCM/APNs), SMS gateway, and WhatsApp behind one internal call; the caller just says "notify this user about X," this service decides channel/template.
**Data touched:** Notification log table (for delivery tracking/debugging).
