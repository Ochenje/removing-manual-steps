package tech.kood.rental.transport;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.Rental;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.repository.exception.DatabaseException;
import tech.kood.rental.repository.exception.DuplicateUsernameException;
import tech.kood.rental.service.ItemService;
import tech.kood.rental.service.RentalService;
import tech.kood.rental.service.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

/**
 * Handles terminal interaction and delegates application rules to the services.
 */
public class CliHandler {

    private static final int PAGE_SIZE = 5;

    private final UserService userService;
    private final ItemService itemService;
    private final RentalService rentalService;
    private final Scanner scanner;
    private UserService.AuthenticatedUser currentUser;
    private boolean running = true;
    private boolean endOfInput;

    public CliHandler(
            UserService userService,
            ItemService itemService,
            RentalService rentalService) {
        this(userService, itemService, rentalService, new Scanner(System.in));
    }

    public CliHandler(
            UserService userService,
            ItemService itemService,
            RentalService rentalService,
            Scanner scanner) {
        this.userService = Objects.requireNonNull(userService, "userService");
        this.itemService = Objects.requireNonNull(itemService, "itemService");
        this.rentalService = Objects.requireNonNull(
                rentalService, "rentalService");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
    }

    public void start() {
        System.out.println("=== Welcome to Multi-User Rental Tracker ===");
        while (running && !endOfInput) {
            if (currentUser == null) {
                showAuthMenu();
            } else {
                showMainMenu();
            }
        }
        System.out.println("Goodbye!");
    }

    private void showAuthMenu() {
        System.out.println("\n=== Rental tracker ===");
        System.out.println("1) Log in");
        System.out.println("2) Register");
        System.out.println("3) Exit");

        String choice = readLine("> ");
        if (choice == null) {
            return;
        }

        switch (choice.trim()) {
            case "1" -> handleLogin();
            case "2" -> handleRegistration();
            case "3" -> running = false;
            default -> System.out.println("Invalid option.");
        }
    }

    private void showMainMenu() {
        System.out.println("\n=== Rental tracker ===");
        System.out.println("1) List an item");
        System.out.println("2) View my inventory");
        System.out.println("3) Browse listings");
        System.out.println("4) Confirm a return");
        System.out.println("5) Log out");
        System.out.println("6) Exit");

        String choice = readLine("> ");
        if (choice == null) {
            return;
        }

        switch (choice.trim()) {
            case "1" -> handleListItem();
            case "2" -> handleViewInventory();
            case "3" -> handleBrowseListings();
            case "4" -> handleConfirmReturns();
            case "5" -> {
                currentUser = null;
                System.out.println("Logged out.");
            }
            case "6" -> running = false;
            default -> System.out.println("Invalid option.");
        }
    }

    private void handleLogin() {
        String username = readLine("Enter username: ");
        if (username == null) {
            return;
        }
        String password = readLine("Enter password: ");
        if (password == null) {
            return;
        }

        runServiceAction(() -> {
            UserService.AuthenticatedUser authenticated =
                    userService.login(username.trim(), password);
            currentUser = authenticated;
            System.out.println("Login successful! Welcome, "
                    + authenticated.username() + ".");
        });
    }

    private void handleRegistration() {
        String username = readLine("Choose a username: ");
        if (username == null) {
            return;
        }
        String password = readLine("Choose a password: ");
        if (password == null) {
            return;
        }

        runServiceAction(() -> {
            userService.register(username.trim(), password);
            System.out.println("Registration successful! You can now log in.");
        });
    }

    private void handleListItem() {
        String name = readLine("Item name: ");
        if (name == null) {
            return;
        }
        String description = readLine("Description: ");
        if (description == null) {
            return;
        }
        String costInput = readLine("Cost per day: ");
        if (costInput == null) {
            return;
        }

        final double cost;
        try {
            cost = Double.parseDouble(costInput.trim());
        } catch (NumberFormatException exception) {
            System.out.println("Enter a valid daily cost.");
            return;
        }

        runServiceAction(() -> {
            itemService.listItem(
                    currentUser.id(), name.trim(), description.trim(), cost);
            System.out.println("Item listed successfully!");
        });
    }

    private void handleViewInventory() {
        int page = 1;
        while (true) {
            List<Item> items;
            long totalItems;
            try {
                totalItems = itemService.countMyInventory(currentUser.id());
                if (totalItems == 0) {
                    System.out.println("Your inventory is empty.");
                    return;
                }
                page = Math.min(page, pageCount(totalItems));
                items = itemService.viewMyInventory(currentUser.id(), page);
            } catch (RuntimeException exception) {
                reportServiceError(exception);
                return;
            }

            System.out.println("\n=== My Inventory (page "
                    + page + " of " + pageCount(totalItems) + ") ===");
            for (int i = 0; i < items.size(); i++) {
                printItem(i + 1, items.get(i), true);
            }
            int nextOption = -1;
            int previousOption = -1;
            int menuOption = items.size() + 1;
            if (page < pageCount(totalItems)) {
                nextOption = menuOption++;
                System.out.println(nextOption + ") Next page");
            }
            if (page > 1) {
                previousOption = menuOption++;
                System.out.println(previousOption + ") Previous page");
            }
            System.out.println(menuOption + ") Back to menu");

            String input = readLine("> ");
            if (input == null) {
                return;
            }
            Integer choice = parseSelection(input);
            if (choice == null) {
                continue;
            }
            if (choice == menuOption) {
                return;
            }
            if (nextOption != -1 && choice == nextOption) {
                page++;
            } else if (previousOption != -1 && choice == previousOption) {
                page--;
            } else if (choice >= 1 && choice <= items.size()) {
                handleManageItem(items.get(choice - 1));
                if (endOfInput) {
                    return;
                }
            } else {
                System.out.println("Invalid selection.");
            }
        }
    }

    private void handleManageItem(Item item) {
        System.out.println("\nManaging: " + item.name() + " [" + item.status() + "]");
        System.out.println("1) Delist item");
        System.out.println("2) Relist item");
        System.out.println("3) Back");

        String choice = readLine("> ");
        if (choice == null) {
            return;
        }

        switch (choice.trim()) {
            case "1" -> runServiceAction(() -> {
                itemService.delistItem(currentUser.id(), item.id());
                System.out.println("Item delisted.");
            });
            case "2" -> runServiceAction(() -> {
                itemService.relistItem(currentUser.id(), item.id());
                System.out.println("Item relisted.");
            });
            case "3" -> {
                // Return to the inventory menu.
            }
            default -> System.out.println("Invalid option.");
        }
    }

    private void handleBrowseListings() {
        int currentPage = 1;
        while (true) {
            final List<Item> availableItems;
            final long totalItems;
            try {
                totalItems =
                        itemService.countAvailableItems(currentUser.id());
                if (totalItems == 0) {
                    System.out.println("No items available to browse.");
                    return;
                }
                currentPage = Math.min(currentPage, pageCount(totalItems));
                availableItems =
                        itemService.browseAvailableItems(
                                currentUser.id(), currentPage);
            } catch (RuntimeException exception) {
                reportServiceError(exception);
                return;
            }

            System.out.println("\n=== Listings (page "
                    + currentPage + " of " + pageCount(totalItems) + ") ===");
            for (int i = 0; i < availableItems.size(); i++) {
                printItem(i + 1, availableItems.get(i), false);
            }

            int nextOption = -1;
            int previousOption = -1;
            int menuOption = availableItems.size() + 1;
            if (currentPage < pageCount(totalItems)) {
                nextOption = menuOption++;
                System.out.println(nextOption + ") Next page");
            }
            if (currentPage > 1) {
                previousOption = menuOption++;
                System.out.println(previousOption + ") Previous page");
            }
            System.out.println(menuOption + ") Back to menu");

            String input = readLine("> ");
            if (input == null) {
                return;
            }
            Integer selection = parseSelection(input);
            if (selection == null) {
                continue;
            }
            if (selection == menuOption) {
                return;
            }
            if (nextOption != -1 && selection == nextOption) {
                currentPage++;
            } else if (previousOption != -1 && selection == previousOption) {
                currentPage--;
            } else if (selection >= 1 && selection <= availableItems.size()) {
                showDetailedView(availableItems.get(selection - 1));
                if (endOfInput) {
                    return;
                }
            } else {
                System.out.println("Invalid selection.");
            }
        }
    }

    private void showDetailedView(Item item) {
        System.out.println("\n=== Item details ===");
        System.out.println("Name: " + item.name());
        System.out.println("Description: " + item.description());
        System.out.println("Cost per day: " + formatCost(item.costPerDay()));
        System.out.println("Owner: " + userService.usernameForId(item.ownerId()));
        System.out.println("Status: " + item.status());
        System.out.println("1) Rent this item");
        System.out.println("2) Back to listings");
        String choice = readLine("> ");
        if (choice == null) {
            return;
        }
        if (choice.trim().equals("1")) {
            String daysInput = readLine("Number of days: ");
            if (daysInput == null) {
                return;
            }
            try {
                int days = Integer.parseInt(daysInput.trim());
                runServiceAction(() -> {
                    rentalService.rentItem(
                            currentUser.id(), item.id(), days);
                    System.out.println("Item rented successfully.");
                });
            } catch (NumberFormatException exception) {
                System.out.println("Enter a valid number of days.");
            }
        } else if (!choice.trim().equals("2")) {
            System.out.println("Invalid option.");
        }
    }

    private void handleConfirmReturns() {
        int page = 1;
        while (true) {
            final List<Item> rentedItems;
            final long totalItems;
            try {
                totalItems =
                        itemService.countRentedInventory(currentUser.id());
                if (totalItems == 0) {
                    System.out.println("You have no items awaiting return.");
                    return;
                }
                page = Math.min(page, pageCount(totalItems));
                rentedItems =
                        itemService.viewRentedInventory(currentUser.id(), page);
            } catch (RuntimeException exception) {
                reportServiceError(exception);
                return;
            }

            System.out.println("\n=== Items Awaiting Return (page "
                    + page + " of " + pageCount(totalItems) + ") ===");
            for (int i = 0; i < rentedItems.size(); i++) {
                printItem(i + 1, rentedItems.get(i), true);
            }

            int nextOption = -1;
            int previousOption = -1;
            int menuOption = rentedItems.size() + 1;
            if (page < pageCount(totalItems)) {
                nextOption = menuOption++;
                System.out.println(nextOption + ") Next page");
            }
            if (page > 1) {
                previousOption = menuOption++;
                System.out.println(previousOption + ") Previous page");
            }
            System.out.println(menuOption + ") Back to menu");

            String input = readLine("> ");
            if (input == null) {
                return;
            }
            Integer choice = parseSelection(input);
            if (choice == null) {
                continue;
            }
            if (choice == menuOption) {
                return;
            }
            if (nextOption != -1 && choice == nextOption) {
                page++;
            } else if (previousOption != -1 && choice == previousOption) {
                page--;
            } else if (choice >= 1 && choice <= rentedItems.size()) {
                showReturnDetails(rentedItems.get(choice - 1));
                if (endOfInput) {
                    return;
                }
            } else {
                System.out.println("Invalid selection.");
            }
        }
    }

    private void showReturnDetails(Item item) {
        final Rental rental;
        try {
            rental = rentalService.getActiveRentalForOwner(
                    currentUser.id(), item.id());
        } catch (RuntimeException exception) {
            reportServiceError(exception);
            return;
        }

        DateTimeFormatter dateTimeFormat =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        LocalDateTime expectedReturn = rental.rentedAt().plusDays(rental.days());

        System.out.println("\n=== Return details ===");
        System.out.println("Item: " + item.name());
        System.out.println("Renter: " + userService.usernameForId(rental.renterId()));
        System.out.println("Rental started: " + rental.rentedAt().format(dateTimeFormat));
        System.out.println("Expected return: " + expectedReturn.format(dateTimeFormat));
        System.out.println("1) Confirm return");
        System.out.println("2) Back");

        String choice = readLine("> ");
        if (choice == null) {
            return;
        }
        if (choice.trim().equals("1")) {
            runServiceAction(() -> {
                rentalService.confirmReturn(currentUser.id(), item.id());
                System.out.println("Return confirmed.");
            });
        } else if (!choice.trim().equals("2")) {
            System.out.println("Invalid option.");
        }
    }

    private int pageCount(long itemCount) {
        return (int) ((itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void printItem(int index, Item item, boolean includeStatus) {
        String line = String.format(
                Locale.ROOT, "%d) %s | %.2f/day | owner %s",
                index,
                item.name(),
                item.costPerDay(),
                userService.usernameForId(item.ownerId()));
        if (includeStatus) {
            line += " [" + item.status() + "]";
        }
        System.out.println(line);
    }

    private String formatCost(double cost) {
        return String.format(Locale.ROOT, "%.2f", cost);
    }

    private Integer parseSelection(String input) {
        try {
            return Integer.valueOf(input.trim());
        } catch (NumberFormatException exception) {
            System.out.println("Please enter a valid numeric choice.");
            return null;
        }
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            endOfInput = true;
            return null;
        }
        return scanner.nextLine();
    }

    private void runServiceAction(Runnable action) {
        try {
            action.run();
        } catch (ValidationException | DuplicateUsernameException exception) {
            System.out.println(exception.getMessage());
        } catch (DatabaseException exception) {
            System.out.println(exception.getMessage());
        }
    }

    private void reportServiceError(RuntimeException exception) {
        if (exception instanceof ValidationException
                || exception instanceof DuplicateUsernameException
                || exception instanceof DatabaseException) {
            System.out.println(exception.getMessage());
            return;
        }
        throw exception;
    }
}
