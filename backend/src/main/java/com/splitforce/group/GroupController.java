package com.splitforce.group;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.splitforce.expense.Expense;
import com.splitforce.expense.ExpenseRepository;
import com.splitforce.user.User;
import com.splitforce.user.UserRepository;
import com.splitforce.user.UserService;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final BigDecimal SETTLE_EPSILON = new BigDecimal("0.01");
    private static final int MAX_MEMBERS_PER_GROUP = 100;
    private static final int DESCRIPTION_MAX_LEN = 140;
    private static final int CATEGORY_MAX_LEN = 40;
    static final String PAYMENT_CATEGORY = "Payment";

    private final UserService userService;
    private final UserRepository users;
    private final GroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final ExpenseRepository expenses;
    private final SettlementLedger settlements;

    public GroupController(UserService userService,
                           UserRepository users,
                           GroupRepository groups,
                           GroupMemberRepository groupMembers,
                           ExpenseRepository expenses,
                           SettlementLedger settlements) {
        this.userService = userService;
        this.users = users;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    @GetMapping
    public List<Map<String, Object>> myGroups(@AuthenticationPrincipal OAuth2User principal) {
        User me = userService.resolveOrCreate(principal);
        List<GroupMember> memberships = groupMembers.listByUser(me.getUserId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (GroupMember m : memberships) {
            groups.findById(m.getGroupId()).ifPresent(g -> out.add(Map.of(
                    "groupId", g.getGroupId(),
                    "name", g.getName(),
                    "type", g.getType(),
                    "baseCurrency", g.getBaseCurrency()
            )));
        }
        return out;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createGroup(@AuthenticationPrincipal OAuth2User principal,
                                                           @RequestBody CreateGroupRequest req) {
        User me = userService.resolveOrCreate(principal);

        String name = req.name == null ? "" : req.name.trim();
        if (name.isEmpty()) throw badRequest("name is required");
        if (name.length() > 80) name = name.substring(0, 80);

        GroupType type;
        try {
            type = GroupType.valueOf(req.type == null ? "" : req.type.trim());
        } catch (IllegalArgumentException e) {
            throw badRequest("type must be one of trip, home, general");
        }

        String currency = req.baseCurrency == null ? "" : req.baseCurrency.trim().toUpperCase();
        if (currency.length() != 3) throw badRequest("baseCurrency must be a 3-letter code");

        List<MemberInput> memberInputs = req.members == null ? List.of() : req.members;
        if (memberInputs.size() >= MAX_MEMBERS_PER_GROUP) {
            throw badRequest("a group can have at most " + MAX_MEMBERS_PER_GROUP + " members");
        }

        String groupId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Group g = new Group();
        g.setGroupId(groupId);
        g.setName(name);
        g.setType(type.name());
        g.setBaseCurrency(currency);
        g.setOwnerUserId(me.getUserId());
        g.setCreatedAt(now);
        groups.save(g);

        Set<String> seenUserIds = new HashSet<>();
        GroupMember ownerRow = new GroupMember();
        ownerRow.setGroupId(groupId);
        ownerRow.setUserId(me.getUserId());
        ownerRow.setDisplayName(me.getName() != null ? me.getName() : me.getEmail());
        ownerRow.setActive(true);
        ownerRow.setJoinedAt(now);
        groupMembers.save(ownerRow);
        seenUserIds.add(me.getUserId());

        for (MemberInput mi : memberInputs) {
            User u;
            try {
                u = userService.findOrCreateMember(mi.displayName, mi.email);
            } catch (IllegalArgumentException e) {
                throw badRequest(e.getMessage());
            }
            if (!seenUserIds.add(u.getUserId())) continue;
            GroupMember row = new GroupMember();
            row.setGroupId(groupId);
            row.setUserId(u.getUserId());
            row.setDisplayName(mi.displayName != null && !mi.displayName.trim().isEmpty()
                    ? mi.displayName.trim()
                    : (u.getName() != null ? u.getName() : u.getEmail()));
            row.setActive(true);
            row.setJoinedAt(now);
            groupMembers.save(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", groupId);
        body.put("name", name);
        body.put("type", type.name());
        body.put("baseCurrency", currency);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{groupId}")
    public Map<String, Object> updateGroup(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable("groupId") String groupId,
                                           @RequestBody UpdateGroupRequest req) {
        User me = userService.resolveOrCreate(principal);
        Group g = groups.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group not found"));
        boolean iAmMember = groupMembers.listByGroup(groupId).stream()
                .anyMatch(m -> me.getUserId().equals(m.getUserId()));
        if (!iAmMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this group");
        }

        if (req.baseCurrency != null && !req.baseCurrency.trim().equalsIgnoreCase(g.getBaseCurrency())) {
            throw badRequest("base currency cannot be changed after group creation");
        }
        boolean changed = false;
        if (req.name != null) {
            String trimmed = req.name.trim();
            if (trimmed.isEmpty()) throw badRequest("name must not be blank");
            if (trimmed.length() > 80) trimmed = trimmed.substring(0, 80);
            if (!trimmed.equals(g.getName())) {
                g.setName(trimmed);
                changed = true;
            }
        }
        if (changed) groups.save(g);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupId", g.getGroupId());
        out.put("name", g.getName());
        out.put("type", g.getType());
        out.put("baseCurrency", g.getBaseCurrency());
        return out;
    }

    @GetMapping("/{groupId}")
    public Map<String, Object> getGroup(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable("groupId") String groupId) {
        User me = userService.resolveOrCreate(principal);
        Group g = groups.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group not found"));

        List<GroupMember> memberships = groupMembers.listByGroup(groupId);
        boolean iAmMember = memberships.stream().anyMatch(m -> me.getUserId().equals(m.getUserId()));
        if (!iAmMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this group");
        }

        List<Expense> groupExpenses = expenses.listByGroupConsistent(groupId);

        Map<String, BigDecimal> netByUser = netByUser(g, groupExpenses);
        for (GroupMember m : memberships) netByUser.putIfAbsent(m.getUserId(), BigDecimal.ZERO);

        Map<String, String> nameByUser = new HashMap<>();
        List<Map<String, Object>> memberDtos = new ArrayList<>();
        for (GroupMember m : memberships) {
            String displayName = m.getDisplayName();
            nameByUser.put(m.getUserId(), displayName);
            BigDecimal net = netByUser.getOrDefault(m.getUserId(), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("userId", m.getUserId());
            dto.put("displayName", displayName);
            dto.put("active", m.isActive());
            dto.put("you", me.getUserId().equals(m.getUserId()));
            dto.put("net", net);
            users.findById(m.getUserId()).ifPresent(u -> {
                dto.put("email", u.getEmail());
                dto.put("placeholder", u.isPlaceholder());
                dto.put("picture", userService.resolvePictureUrl(u));
            });
            memberDtos.add(dto);
        }

        List<Map<String, Object>> expenseDtos = new ArrayList<>();
        for (Expense e : groupExpenses) {
            BigDecimal myShare = e.getShares() == null ? null : e.getShares().get(me.getUserId());
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("expenseId", e.getExpenseId());
            dto.put("date", e.getDate());
            dto.put("description", e.getDescription());
            dto.put("category", e.getCategory());
            dto.put("cost", e.getCost());
            dto.put("currency", e.getCurrency());
            dto.put("payment", e.isPayment());
            dto.put("myNet", myShare == null ? BigDecimal.ZERO : myShare);
            dto.put("paidByUserId", e.getPaidByUserId() != null ? e.getPaidByUserId() : derivePayer(e));
            dto.put("shares", e.getShares() == null ? Map.of() : e.getShares());
            dto.put("notes", e.getNotes());
            dto.put("receiptKey", e.getReceiptKey());
            dto.put("splitMode", e.getSplitMode());
            dto.put("splitValues", e.getSplitValues());
            expenseDtos.add(dto);
        }
        expenseDtos.sort(Comparator.comparing(
                (Map<String, Object> r) -> (String) r.get("expenseId"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> settlement = computeSettlement(netByUser, nameByUser);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupId", g.getGroupId());
        out.put("name", g.getName());
        out.put("type", g.getType());
        out.put("baseCurrency", g.getBaseCurrency());
        out.put("fxRates", g.getFxRates() == null ? Map.of() : g.getFxRates());
        out.put("ownerUserId", g.getOwnerUserId());
        out.put("createdAt", g.getCreatedAt());
        out.put("notes", g.getNotes());
        out.put("notesUpdatedAt", g.getNotesUpdatedAt());
        out.put("notesUpdatedByUserId", g.getNotesUpdatedByUserId());
        out.put("members", memberDtos);
        out.put("expenses", expenseDtos);
        out.put("settlement", settlement);
        return out;
    }

    @PostMapping("/{groupId}/members")
    public Map<String, Object> addMembers(@AuthenticationPrincipal OAuth2User principal,
                                          @PathVariable("groupId") String groupId,
                                          @RequestBody AddMembersRequest req) {
        User me = userService.resolveOrCreate(principal);
        requireMemberGroup(me, groupId);
        List<MemberInput> inputs = req == null || req.members == null ? List.of() : req.members;
        if (inputs.isEmpty()) throw badRequest("members is required");

        Map<String, GroupMember> existing = new HashMap<>();
        for (GroupMember m : groupMembers.listByGroup(groupId)) existing.put(m.getUserId(), m);
        if (existing.size() + inputs.size() > MAX_MEMBERS_PER_GROUP) {
            throw badRequest("a group can have at most " + MAX_MEMBERS_PER_GROUP + " members");
        }

        String now = Instant.now().toString();
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (MemberInput mi : inputs) {
            User u;
            try {
                u = userService.findOrCreateMember(mi.displayName, mi.email);
            } catch (IllegalArgumentException e) {
                throw badRequest(e.getMessage());
            }
            GroupMember row = existing.get(u.getUserId());
            if (row != null && row.isActive()) {
                skipped.add(memberSummary(row));
                continue;
            }
            if (row == null) {
                row = new GroupMember();
                row.setGroupId(groupId);
                row.setUserId(u.getUserId());
                row.setDisplayName(mi.displayName != null && !mi.displayName.trim().isEmpty()
                        ? mi.displayName.trim()
                        : (u.getName() != null ? u.getName() : u.getEmail()));
                row.setJoinedAt(now);
            }
            row.setActive(true);
            groupMembers.save(row);
            existing.put(row.getUserId(), row);
            added.add(memberSummary(row));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupId", groupId);
        out.put("added", added);
        out.put("skipped", skipped);
        return out;
    }

    private static Map<String, Object> memberSummary(GroupMember m) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("userId", m.getUserId());
        dto.put("displayName", m.getDisplayName());
        return dto;
    }

    private static final int NOTES_MAX_LEN = 10_000;

    @PatchMapping("/{groupId}/notes")
    public Map<String, Object> updateNotes(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable("groupId") String groupId,
                                           @RequestBody UpdateNotesRequest req) {
        User me = userService.resolveOrCreate(principal);
        Group g = requireMemberGroup(me, groupId);
        String value = req == null || req.notes == null ? "" : req.notes;
        if (value.length() > NOTES_MAX_LEN) {
            throw badRequest("notes must be " + NOTES_MAX_LEN + " characters or fewer");
        }
        String trimmed = value.strip();
        g.setNotes(trimmed.isEmpty() ? null : value);
        g.setNotesUpdatedAt(Instant.now().toString());
        g.setNotesUpdatedByUserId(me.getUserId());
        groups.save(g);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupId", g.getGroupId());
        out.put("notes", g.getNotes());
        out.put("notesUpdatedAt", g.getNotesUpdatedAt());
        out.put("notesUpdatedByUserId", g.getNotesUpdatedByUserId());
        return out;
    }

    @GetMapping("/{groupId}/export")
    public ResponseEntity<String> exportCsv(@AuthenticationPrincipal OAuth2User principal,
                                            @PathVariable("groupId") String groupId) {
        User me = userService.resolveOrCreate(principal);
        Group g = requireMemberGroup(me, groupId);
        List<GroupMember> memberships = groupMembers.listByGroup(groupId);
        memberships.sort(Comparator.comparing(GroupMember::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<Expense> groupExpenses = new ArrayList<>(expenses.listByGroup(groupId));
        groupExpenses.sort(Comparator.comparing(Expense::getExpenseId,
                Comparator.nullsLast(Comparator.naturalOrder())));

        StringBuilder csv = new StringBuilder();
        List<String> header = new ArrayList<>(List.of(
                "date", "description", "category", "cost", "currency", "base_cost", "payer"));
        for (GroupMember m : memberships) {
            header.add(csvText(m.getDisplayName() + " (net " + g.getBaseCurrency() + ")"));
        }
        csv.append(csvRow(header)).append('\n');

        Map<String, String> nameByUser = new HashMap<>();
        for (GroupMember m : memberships) nameByUser.put(m.getUserId(), m.getDisplayName());

        for (Expense e : groupExpenses) {
            BigDecimal rate = rateToBase(g, e.getCurrency());
            BigDecimal baseCost = e.getCost() == null ? BigDecimal.ZERO
                    : e.getCost().multiply(rate).setScale(2, RoundingMode.HALF_UP);
            String payer = e.getPaidByUserId() != null
                    ? nameByUser.getOrDefault(e.getPaidByUserId(), "")
                    : nameByUser.getOrDefault(derivePayer(e), "");
            List<String> row = new ArrayList<>(List.of(
                    e.getDate() == null ? "" : e.getDate(),
                    csvText(e.getDescription()),
                    csvText(e.getCategory()),
                    e.getCost() == null ? "" : e.getCost().toPlainString(),
                    e.getCurrency() == null ? "" : e.getCurrency(),
                    baseCost.toPlainString(),
                    csvText(payer)
            ));
            for (GroupMember m : memberships) {
                BigDecimal share = e.getShares() == null ? null : e.getShares().get(m.getUserId());
                if (share == null) {
                    row.add("0.00");
                } else {
                    row.add(share.multiply(rate).setScale(2, RoundingMode.HALF_UP).toPlainString());
                }
            }
            csv.append(csvRow(row)).append('\n');
        }

        String slug = g.getName() == null ? "group" : g.getName()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) slug = "group";
        String filename = slug + "-" + java.time.LocalDate.now().toString().replace("-", "") + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    private static String csvRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvCell(cells.get(i)));
        }
        return sb.toString();
    }

    /**
     * User-entered text: a leading = + - @ (or tab/CR) would run as a formula in Excel or Sheets,
     * so it is prefixed with an apostrophe. Numeric cells skip this so negative amounts stay numbers.
     */
    private static String csvText(String s) {
        if (s == null || s.isEmpty()) return "";
        return "=+-@\t\r".indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
    }

    private static String csvCell(String s) {
        if (s == null) return "";
        boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String out = s.replace("\"", "\"\"");
        return quote ? "\"" + out + "\"" : out;
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@AuthenticationPrincipal OAuth2User principal,
                                            @PathVariable("groupId") String groupId) {
        User me = userService.resolveOrCreate(principal);
        Group g = groups.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group not found"));
        if (!me.getUserId().equals(g.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the owner can delete this group");
        }
        List<GroupMember> memberships = groupMembers.listByGroup(groupId);
        List<Expense> groupExpenses = expenses.listByGroup(groupId);

        Map<String, BigDecimal> netByUser = netByUser(g, groupExpenses);
        for (GroupMember m : memberships) netByUser.putIfAbsent(m.getUserId(), BigDecimal.ZERO);
        for (Map.Entry<String, BigDecimal> entry : netByUser.entrySet()) {
            BigDecimal rounded = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            if (rounded.abs().compareTo(SETTLE_EPSILON) >= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "cannot delete: unsettled balances remain. Settle up first.");
            }
        }

        for (Expense e : groupExpenses) expenses.delete(e.getGroupId(), e.getExpenseId());
        for (GroupMember m : memberships) groupMembers.delete(m.getGroupId(), m.getUserId());
        groups.delete(groupId);
        settlements.release(groupId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{groupId}/fx-rates/{code}")
    public Map<String, Object> setFxRate(@AuthenticationPrincipal OAuth2User principal,
                                         @PathVariable("groupId") String groupId,
                                         @PathVariable("code") String rawCode,
                                         @RequestBody FxRateRequest req) {
        User me = userService.resolveOrCreate(principal);
        Group g = requireMemberGroup(me, groupId);
        String code = normalizeCurrency(rawCode);
        if (code.equalsIgnoreCase(g.getBaseCurrency())) {
            throw badRequest("cannot set an FX rate for the base currency");
        }
        if (req == null || req.rate == null || req.rate.signum() <= 0) {
            throw badRequest("rate must be a positive number");
        }
        Map<String, BigDecimal> rates = g.getFxRates() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(g.getFxRates());
        rates.put(code, req.rate);
        g.setFxRates(rates);
        groups.save(g);
        return fxRatesResponse(g);
    }

    @DeleteMapping("/{groupId}/fx-rates/{code}")
    public Map<String, Object> deleteFxRate(@AuthenticationPrincipal OAuth2User principal,
                                            @PathVariable("groupId") String groupId,
                                            @PathVariable("code") String rawCode) {
        User me = userService.resolveOrCreate(principal);
        Group g = requireMemberGroup(me, groupId);
        String code = normalizeCurrency(rawCode);
        boolean used = expenses.listByGroup(groupId).stream()
                .anyMatch(e -> code.equalsIgnoreCase(e.getCurrency()));
        if (used) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "cannot remove " + code + ": expenses in this currency exist. Delete them first.");
        }
        if (g.getFxRates() != null && g.getFxRates().containsKey(code)) {
            Map<String, BigDecimal> rates = new LinkedHashMap<>(g.getFxRates());
            rates.remove(code);
            g.setFxRates(rates.isEmpty() ? null : rates);
            groups.save(g);
        }
        return fxRatesResponse(g);
    }

    /**
     * Records a settle-up payment. Unlike a plain expense it is checked against the live balances: the payer
     * must really owe the payee at least this much right now, so a stale screen (or a second person recording
     * the same payment) is rejected instead of flipping the balance the other way. Safe to retry with the same
     * requestId.
     */
    @PostMapping("/{groupId}/settlements")
    public ResponseEntity<Map<String, Object>> settle(@AuthenticationPrincipal OAuth2User principal,
                                                      @PathVariable("groupId") String groupId,
                                                      @RequestBody SettleRequest req) {
        User me = userService.resolveOrCreate(principal);
        Group g = requireMemberGroup(me, groupId);
        if (req == null || req.fromUserId == null || req.toUserId == null) {
            throw badRequest("fromUserId and toUserId are required");
        }
        if (req.fromUserId.equals(req.toUserId)) throw badRequest("payer and receiver must differ");
        if (req.amount == null || req.amount.signum() <= 0) throw badRequest("amount must be > 0");
        BigDecimal amount = req.amount.setScale(2, RoundingMode.HALF_UP);
        String requestId = req.requestId == null || req.requestId.isBlank()
                ? UUID.randomUUID().toString() : req.requestId.trim();
        if (!requestId.matches("[A-Za-z0-9-]{8,64}")) throw badRequest("invalid requestId");

        CreateExpenseRequest asExpense = new CreateExpenseRequest();
        asExpense.description = req.note == null || req.note.isBlank() ? "Settlement" : req.note;
        asExpense.date = req.date;
        asExpense.category = PAYMENT_CATEGORY;
        asExpense.cost = amount;
        asExpense.currency = g.getBaseCurrency();
        asExpense.paidByUserId = req.fromUserId;
        asExpense.splitMode = "amount";
        Participant to = new Participant();
        to.userId = req.toUserId;
        to.value = amount;
        asExpense.participants = List.of(to);

        Expense e = buildExpense(g, asExpense, activeMemberIds(groupId), me.getUserId());
        e.setPayment(true);
        e.setCreatedAt(Instant.now().toString());
        e.setCreatedByUserId(me.getUserId());

        String expenseId = settlements.append(groupId, requestKey(requestId), current -> {
            requireOutstandingDebt(g, current, req.fromUserId, req.toUserId, amount);
            return e;
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expenseId", expenseId);
        out.put("groupId", groupId);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    /** Net balance per user in the group's base currency, unrounded. */
    static Map<String, BigDecimal> netByUser(Group g, List<Expense> groupExpenses) {
        Map<String, BigDecimal> net = new HashMap<>();
        for (Expense e : groupExpenses) {
            if (e.getShares() == null) continue;
            BigDecimal rate = rateToBase(g, e.getCurrency());
            for (Map.Entry<String, BigDecimal> s : e.getShares().entrySet()) {
                if (s.getValue() == null) continue;
                net.merge(s.getKey(), s.getValue().multiply(rate), BigDecimal::add);
            }
        }
        return net;
    }

    /** Rejects a payment the balances don't support: the payer must owe, and the payee be owed, at least {@code amount}. */
    static void requireOutstandingDebt(Group g, List<Expense> current, String from, String to, BigDecimal amount) {
        Map<String, BigDecimal> net = netByUser(g, current);
        BigDecimal owes = net.getOrDefault(from, BigDecimal.ZERO).negate();
        BigDecimal owed = net.getOrDefault(to, BigDecimal.ZERO);
        BigDecimal slack = SETTLE_EPSILON.negate();
        if (owes.subtract(amount).compareTo(slack) < 0 || owed.subtract(amount).compareTo(slack) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "balances have changed: this payment is no longer owed. Refresh to see what is still due.");
        }
    }

    private static String requestKey(String requestId) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(requestId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h, 0, 8);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @PostMapping("/{groupId}/expenses")
    public ResponseEntity<Map<String, Object>> createExpense(@AuthenticationPrincipal OAuth2User principal,
                                                             @PathVariable("groupId") String groupId,
                                                             @RequestBody CreateExpenseRequest req) {
        User me = userService.resolveOrCreate(principal);
        Group g = requireMemberGroup(me, groupId);
        Set<String> memberIds = activeMemberIds(groupId);

        Expense e = buildExpense(g, req, memberIds, me.getUserId());
        if (PAYMENT_CATEGORY.equalsIgnoreCase(e.getCategory())) {
            // Settlements are validated against live balances, which only the settlements endpoint does.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "settle-up has moved. Refresh the page and record the payment again.");
        }
        e.setExpenseId(newExpenseId(e.getDate()));
        e.setCreatedAt(Instant.now().toString());
        e.setCreatedByUserId(me.getUserId());
        expenses.save(e);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expenseId", e.getExpenseId());
        out.put("groupId", groupId);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    /** Full replace of an expense. Same body and rules as create. */
    @PutMapping("/{groupId}/expenses/{expenseId}")
    public Map<String, Object> updateExpense(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable("groupId") String groupId,
                                             @PathVariable("expenseId") String expenseId,
                                             @RequestBody CreateExpenseRequest req) {
        User me = userService.resolveOrCreate(principal);
        Group g = requireMemberGroup(me, groupId);
        Expense old = expenses.findById(groupId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "expense not found"));

        // People already on the expense stay selectable even if they've since been removed from the group.
        Set<String> allowedIds = activeMemberIds(groupId);
        if (old.getShares() != null) allowedIds.addAll(old.getShares().keySet());
        if (old.getPaidByUserId() != null) allowedIds.add(old.getPaidByUserId());

        Expense e = buildExpense(g, req, allowedIds, me.getUserId());
        e.setCreatedAt(old.getCreatedAt());
        e.setCreatedByUserId(old.getCreatedByUserId());
        e.setPayment(old.isPayment());
        // The sort key starts with the date, so a date change moves the expense to a new key.
        boolean rekey = !e.getDate().equals(old.getDate()) || !expenseId.startsWith(e.getDate() + "#");
        e.setExpenseId(rekey ? newExpenseId(e.getDate()) : expenseId);
        expenses.save(e);
        if (rekey) expenses.delete(groupId, expenseId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expenseId", e.getExpenseId());
        out.put("groupId", groupId);
        return out;
    }

    private Set<String> activeMemberIds(String groupId) {
        Set<String> ids = new HashSet<>();
        for (GroupMember m : groupMembers.listByGroup(groupId)) if (m.isActive()) ids.add(m.getUserId());
        return ids;
    }

    private static String newExpenseId(String date) {
        return date + "#" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Validates a create/update body and builds the expense, minus its id and audit fields. */
    private static Expense buildExpense(Group g, CreateExpenseRequest req, Set<String> memberIds, String myUserId) {
        String groupId = g.getGroupId();
        if (req == null) throw badRequest("body required");
        if (req.cost == null || req.cost.signum() <= 0) throw badRequest("cost must be > 0");
        String currency = normalizeCurrency(req.currency);
        if (!currency.equals(g.getBaseCurrency())
                && (g.getFxRates() == null || !g.getFxRates().containsKey(currency))) {
            throw badRequest("currency " + currency + " has no FX rate for this group. Add one in Settings first.");
        }
        String paidBy = req.paidByUserId == null ? myUserId : req.paidByUserId.trim();
        if (paidBy.isEmpty()) paidBy = myUserId;
        if (!memberIds.contains(paidBy)) throw badRequest("payer is not an active member");

        String splitMode = req.splitMode == null ? "equal" : req.splitMode.trim().toLowerCase();
        if (!splitMode.equals("equal") && !splitMode.equals("percent") && !splitMode.equals("amount")) {
            throw badRequest("splitMode must be equal, percent, or amount");
        }
        if (req.participants == null || req.participants.isEmpty()) {
            throw badRequest("at least one participant required");
        }
        List<Participant> participants = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Participant p : req.participants) {
            if (p == null || p.userId == null || p.userId.isBlank()) continue;
            if (!memberIds.contains(p.userId)) throw badRequest("participant is not an active member: " + p.userId);
            if (!seen.add(p.userId)) throw badRequest("duplicate participant: " + p.userId);
            participants.add(p);
        }
        if (participants.isEmpty()) throw badRequest("at least one participant required");

        String notes = req.notes == null ? null : req.notes.trim();
        if (notes != null && notes.length() > 1000) throw badRequest("notes must be 1000 characters or fewer");
        if (notes != null && notes.isEmpty()) notes = null;

        String category = req.category == null ? null : req.category.trim();
        if (category != null && category.isEmpty()) category = null;
        if (category != null && category.length() > CATEGORY_MAX_LEN) {
            throw badRequest("category must be " + CATEGORY_MAX_LEN + " characters or fewer");
        }

        String date;
        try {
            date = req.date == null || req.date.trim().isEmpty()
                    ? java.time.LocalDate.now().toString()
                    : java.time.LocalDate.parse(req.date.trim()).toString();
        } catch (java.time.format.DateTimeParseException ex) {
            throw badRequest("date must be YYYY-MM-DD");
        }

        String description = req.description == null ? "" : req.description.trim();
        if (description.length() > DESCRIPTION_MAX_LEN) description = description.substring(0, DESCRIPTION_MAX_LEN);

        String receiptKey = req.receiptKey == null || req.receiptKey.isBlank() ? null : req.receiptKey.trim();
        // Must be a receipt uploaded to this group (see ReceiptController), not a path into another one.
        if (receiptKey != null && !receiptKey.matches(java.util.regex.Pattern.quote(groupId) + "/[a-f0-9]{32}\\.[a-z0-9]{1,5}")) {
            throw badRequest("invalid receiptKey");
        }

        Map<String, BigDecimal> perParticipantOwed = computeOwed(splitMode, req.cost, participants, paidBy);
        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> owed : perParticipantOwed.entrySet()) {
            shares.merge(owed.getKey(), owed.getValue().negate(), BigDecimal::add);
        }
        shares.merge(paidBy, req.cost, BigDecimal::add);
        shares.values().removeIf(v -> v.signum() == 0);

        // Raw split inputs, so an edit can show exactly what was entered (e.g. 33.33% rather than a derived amount).
        Map<String, BigDecimal> splitValues = new LinkedHashMap<>();
        for (Participant p : participants) {
            splitValues.put(p.userId, "equal".equals(splitMode) || p.value == null ? BigDecimal.ZERO : p.value);
        }

        Expense e = new Expense();
        e.setGroupId(groupId);
        e.setDate(date);
        e.setDescription(description.isEmpty() ? null : description);
        e.setCategory(category);
        e.setCost(req.cost.setScale(2, RoundingMode.HALF_UP));
        e.setCurrency(currency);
        e.setPayment(false);
        e.setShares(shares);
        e.setPaidByUserId(paidBy);
        e.setNotes(notes);
        e.setReceiptKey(receiptKey);
        e.setSplitMode(splitMode);
        e.setSplitValues(splitValues);
        return e;
    }

    private static Map<String, BigDecimal> computeOwed(String mode, BigDecimal cost,
                                                       List<Participant> participants, String payerId) {
        BigDecimal total = cost.setScale(2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> owed = new LinkedHashMap<>();
        if ("equal".equals(mode)) {
            int n = participants.size();
            BigDecimal each = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            BigDecimal sum = BigDecimal.ZERO;
            for (Participant p : participants) {
                owed.put(p.userId, each);
                sum = sum.add(each);
            }
            BigDecimal diff = total.subtract(sum);
            if (diff.signum() != 0) {
                String receiver = participants.stream().anyMatch(p -> p.userId.equals(payerId))
                        ? payerId : participants.get(0).userId;
                owed.merge(receiver, diff, BigDecimal::add);
            }
            return owed;
        }
        if ("percent".equals(mode)) {
            BigDecimal sumPct = BigDecimal.ZERO;
            for (Participant p : participants) {
                if (p.value == null || p.value.signum() < 0) throw badRequest("percent must be non-negative");
                sumPct = sumPct.add(p.value);
            }
            if (sumPct.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw badRequest("percentages must sum to 100 (got " + sumPct + ")");
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (Participant p : participants) {
                BigDecimal share = total.multiply(p.value)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                owed.put(p.userId, share);
                sum = sum.add(share);
            }
            BigDecimal diff = total.subtract(sum);
            if (diff.signum() != 0) {
                String receiver = participants.stream().anyMatch(p -> p.userId.equals(payerId))
                        ? payerId : participants.get(0).userId;
                owed.merge(receiver, diff, BigDecimal::add);
            }
            return owed;
        }
        if ("amount".equals(mode)) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Participant p : participants) {
                if (p.value == null || p.value.signum() < 0) throw badRequest("amount must be non-negative");
                owed.put(p.userId, p.value.setScale(2, RoundingMode.HALF_UP));
                sum = sum.add(p.value);
            }
            if (sum.setScale(2, RoundingMode.HALF_UP).compareTo(total) != 0) {
                throw badRequest("amounts must sum to " + total + " (got " + sum + ")");
            }
            return owed;
        }
        throw badRequest("unknown split mode " + mode);
    }

    @DeleteMapping("/{groupId}/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@AuthenticationPrincipal OAuth2User principal,
                                              @PathVariable("groupId") String groupId,
                                              @PathVariable("expenseId") String expenseId) {
        User me = userService.resolveOrCreate(principal);
        requireMemberGroup(me, groupId);
        Expense e = expenses.findById(groupId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "expense not found"));
        expenses.delete(e.getGroupId(), e.getExpenseId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{groupId}/expenses/{expenseId}")
    public Map<String, Object> patchExpense(@AuthenticationPrincipal OAuth2User principal,
                                            @PathVariable("groupId") String groupId,
                                            @PathVariable("expenseId") String expenseId,
                                            @RequestBody UpdateExpenseRequest req) {
        User me = userService.resolveOrCreate(principal);
        requireMemberGroup(me, groupId);
        Expense e = expenses.findById(groupId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "expense not found"));
        boolean changed = false;
        if (req != null && req.category != null) {
            String c = req.category.trim();
            if (c.length() > CATEGORY_MAX_LEN) {
                throw badRequest("category must be " + CATEGORY_MAX_LEN + " characters or fewer");
            }
            if (!c.equals(e.getCategory() == null ? "" : e.getCategory())) {
                e.setCategory(c.isEmpty() ? null : c);
                changed = true;
            }
        }
        if (req != null && req.description != null) {
            String d = req.description.trim();
            if (d.length() > DESCRIPTION_MAX_LEN) d = d.substring(0, DESCRIPTION_MAX_LEN);
            if (!d.equals(e.getDescription() == null ? "" : e.getDescription())) {
                e.setDescription(d.isEmpty() ? null : d);
                changed = true;
            }
        }
        if (changed) expenses.save(e);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expenseId", e.getExpenseId());
        out.put("date", e.getDate());
        out.put("description", e.getDescription());
        out.put("category", e.getCategory());
        out.put("cost", e.getCost());
        out.put("currency", e.getCurrency());
        return out;
    }

    private static String derivePayer(Expense e) {
        if (e.getShares() == null || e.getShares().isEmpty()) return null;
        String payer = null;
        BigDecimal max = null;
        for (Map.Entry<String, BigDecimal> s : e.getShares().entrySet()) {
            if (s.getValue() == null) continue;
            if (max == null || s.getValue().compareTo(max) > 0) {
                max = s.getValue();
                payer = s.getKey();
            }
        }
        return payer;
    }

    private static BigDecimal rateToBase(Group g, String currency) {
        if (currency == null || currency.isBlank()) return BigDecimal.ONE;
        if (currency.equalsIgnoreCase(g.getBaseCurrency())) return BigDecimal.ONE;
        Map<String, BigDecimal> rates = g.getFxRates();
        if (rates != null) {
            BigDecimal r = rates.get(currency.toUpperCase());
            if (r != null) return r;
        }
        return BigDecimal.ONE;
    }

    private Group requireMemberGroup(User me, String groupId) {
        Group g = groups.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group not found"));
        boolean iAmMember = groupMembers.listByGroup(groupId).stream()
                .anyMatch(m -> me.getUserId().equals(m.getUserId()));
        if (!iAmMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this group");
        }
        return g;
    }

    private static String normalizeCurrency(String raw) {
        String c = raw == null ? "" : raw.trim().toUpperCase();
        if (c.length() != 3 || !c.chars().allMatch(Character::isLetter)) {
            throw badRequest("currency must be a 3-letter code");
        }
        return c;
    }

    private static Map<String, Object> fxRatesResponse(Group g) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupId", g.getGroupId());
        out.put("baseCurrency", g.getBaseCurrency());
        out.put("fxRates", g.getFxRates() == null ? Map.of() : g.getFxRates());
        return out;
    }

    private static List<Map<String, Object>> computeSettlement(Map<String, BigDecimal> netByUser,
                                                               Map<String, String> nameByUser) {
        record Balance(String userId, BigDecimal amount) {}
        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : netByUser.entrySet()) {
            BigDecimal v = e.getValue().setScale(2, RoundingMode.HALF_UP);
            if (v.compareTo(SETTLE_EPSILON) >= 0) creditors.add(new Balance(e.getKey(), v));
            else if (v.compareTo(SETTLE_EPSILON.negate()) <= 0) debtors.add(new Balance(e.getKey(), v.abs()));
        }
        debtors.sort(Comparator.comparing(Balance::amount).reversed());
        creditors.sort(Comparator.comparing(Balance::amount).reversed());

        List<Map<String, Object>> out = new ArrayList<>();
        int di = 0, ci = 0;
        BigDecimal debtLeft = debtors.isEmpty() ? BigDecimal.ZERO : debtors.get(0).amount();
        BigDecimal credLeft = creditors.isEmpty() ? BigDecimal.ZERO : creditors.get(0).amount();
        while (di < debtors.size() && ci < creditors.size()) {
            BigDecimal amount = debtLeft.min(credLeft).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(SETTLE_EPSILON) >= 0) {
                Balance d = debtors.get(di);
                Balance c = creditors.get(ci);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fromUserId", d.userId());
                row.put("fromName", nameByUser.getOrDefault(d.userId(), "?"));
                row.put("toUserId", c.userId());
                row.put("toName", nameByUser.getOrDefault(c.userId(), "?"));
                row.put("amount", amount);
                out.add(row);
            }
            debtLeft = debtLeft.subtract(amount);
            credLeft = credLeft.subtract(amount);
            if (debtLeft.compareTo(SETTLE_EPSILON) < 0) {
                di++;
                debtLeft = di < debtors.size() ? debtors.get(di).amount() : BigDecimal.ZERO;
            }
            if (credLeft.compareTo(SETTLE_EPSILON) < 0) {
                ci++;
                credLeft = ci < creditors.size() ? creditors.get(ci).amount() : BigDecimal.ZERO;
            }
        }
        return out;
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    public static class CreateGroupRequest {
        public String name;
        public String type;
        public String baseCurrency;
        public List<MemberInput> members;
    }

    public static class UpdateGroupRequest {
        public String name;
        public String baseCurrency;
    }

    public static class AddMembersRequest {
        public List<MemberInput> members;
    }

    public static class MemberInput {
        public String displayName;
        public String email;
    }

    public static class FxRateRequest {
        public BigDecimal rate;
    }

    public static class UpdateNotesRequest {
        public String notes;
    }

    public static class UpdateExpenseRequest {
        public String category;
        public String description;
    }

    public static class CreateExpenseRequest {
        public String description;
        public String date;
        public String category;
        public String notes;
        public BigDecimal cost;
        public String currency;
        public String paidByUserId;
        public String splitMode;
        public List<Participant> participants;
        public String receiptKey;
    }

    public static class SettleRequest {
        public String fromUserId;
        public String toUserId;
        public BigDecimal amount;
        public String date;
        public String note;
        public String requestId;
    }

    public static class Participant {
        public String userId;
        public BigDecimal value;
    }
}
