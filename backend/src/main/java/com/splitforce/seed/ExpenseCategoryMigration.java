package com.splitforce.seed;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.splitforce.expense.Expense;
import com.splitforce.expense.ExpenseRepository;

/**
 * One-shot rewrite of expense.category values onto the new taxonomy. Idempotent:
 * already-migrated categories (and 'Payment') are left alone.
 */
@Component
@Order(5)
public class ExpenseCategoryMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExpenseCategoryMigration.class);

    private static final Map<String, String> LEGACY_TO_NEW = new LinkedHashMap<>();
    static {
        LEGACY_TO_NEW.put("Bicycle",                 "Public Transit");
        LEGACY_TO_NEW.put("Bus/train",               "Public Transit");
        LEGACY_TO_NEW.put("Car",                     "Car Rental");
        LEGACY_TO_NEW.put("Cleaning",                "Household Supplies");
        LEGACY_TO_NEW.put("Clothing",                "Other");
        LEGACY_TO_NEW.put("Dining out",              "Dining Out");
        LEGACY_TO_NEW.put("Dining Out",              "Dining Out");
        LEGACY_TO_NEW.put("Electricity",             "Electricity");
        LEGACY_TO_NEW.put("Entertainment - Other",   "Movies & Entertainment");
        LEGACY_TO_NEW.put("Furniture",               "Furniture & Decor");
        LEGACY_TO_NEW.put("Gas/fuel",                "Fuel / Gas");
        LEGACY_TO_NEW.put("General",                 "Other");
        LEGACY_TO_NEW.put("Gifts",                   "Gifts");
        LEGACY_TO_NEW.put("Groceries",               "Groceries");
        LEGACY_TO_NEW.put("Hotel",                   "Accommodation");
        LEGACY_TO_NEW.put("Household supplies",      "Household Supplies");
        LEGACY_TO_NEW.put("Household Supplies",      "Household Supplies");
        LEGACY_TO_NEW.put("Insurance",               "Other");
        LEGACY_TO_NEW.put("Liquor",                  "Liquor / Bars");
        LEGACY_TO_NEW.put("Movies",                  "Movies & Entertainment");
        LEGACY_TO_NEW.put("Parking",                 "Fuel / Gas");
        LEGACY_TO_NEW.put("Pets",                    "Other");
        LEGACY_TO_NEW.put("Plane",                   "Flights");
        LEGACY_TO_NEW.put("Rent",                    "Rent");
        LEGACY_TO_NEW.put("Services",                "Other");
        LEGACY_TO_NEW.put("Sports",                  "Movies & Entertainment");
        LEGACY_TO_NEW.put("Taxi",                    "Rideshare / Taxis");
        LEGACY_TO_NEW.put("Transportation - Other",  "Rideshare / Taxis");
        LEGACY_TO_NEW.put("Trash",                   "Water & Trash");
        LEGACY_TO_NEW.put("TV/Phone/Internet",       "Internet / Wi-Fi");
        LEGACY_TO_NEW.put("Utilities - Other",       "Water & Trash");
        LEGACY_TO_NEW.put("Water",                   "Water & Trash");
        // Old frontend taxonomy
        LEGACY_TO_NEW.put("Food & drink",            "Dining Out");
        LEGACY_TO_NEW.put("Food & Drink",            "Dining Out");
        LEGACY_TO_NEW.put("Transport",               "Rideshare / Taxis");
        LEGACY_TO_NEW.put("Stay",                    "Accommodation");
        LEGACY_TO_NEW.put("Fun",                     "Movies & Entertainment");
        LEGACY_TO_NEW.put("Shopping",                "Gifts");
        LEGACY_TO_NEW.put("Health",                  "Other");
        LEGACY_TO_NEW.put("Bills",                   "Internet / Wi-Fi");
    }

    private static final Set<String> NEW_TAXONOMY = new HashSet<>();
    static {
        Collections.addAll(NEW_TAXONOMY,
                "Groceries", "Dining Out", "Liquor / Bars",
                "Rent", "Electricity", "Internet / Wi-Fi", "Water & Trash",
                "Gas / Heating", "Household Supplies", "Furniture & Decor",
                "Rideshare / Taxis", "Fuel / Gas", "Public Transit",
                "Accommodation", "Flights", "Car Rental",
                "Movies & Entertainment", "Events & Concerts", "Subscriptions", "Gifts",
                "Other", "Payment");
    }

    private final ExpenseRepository expenses;

    public ExpenseCategoryMigration(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = 0;
        int fallback = 0;
        for (Expense e : expenses.scanAll()) {
            if (e.isPayment()) continue;
            String current = e.getCategory();
            if (current == null || current.isBlank()) continue;
            if (NEW_TAXONOMY.contains(current)) continue;

            String replacement = LEGACY_TO_NEW.get(current);
            if (replacement == null) {
                replacement = "Other";
                fallback++;
                log.info("Category migration: unrecognized '{}' on expense {} -> Other", current, e.getExpenseId());
            }
            e.setCategory(replacement);
            expenses.save(e);
            migrated++;
        }
        if (migrated > 0) {
            log.info("Category migration: rewrote {} expenses ({} fell back to 'Other')", migrated, fallback);
        }
    }
}
