package tech.kood.rental.transport;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.repository.exception.DatabaseException;
import tech.kood.rental.repository.exception.DuplicateUsernameException;
import tech.kood.rental.service.ItemService;
import tech.kood.rental.service.UserService;

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
    private final Scanner scanner;
    private UserService.AuthenticatedUser currentUser;
    private boolean running = true;
    private boolean endOfInput;

    public CliHandler(UserService userService, ItemService itemService) {
        this(userService, itemService, new Scanner(System.in));
    }

    public CliHandler(
            UserService userService,
            ItemService itemService,
            Scanner scanner) {
        this.userService = Objects.requireNonNull(userService, "userService");
        this.itemService = Objects.requireNonNull(itemService, "itemService");
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
        System.out.println("4) Log out");
        System.out.println("5) Exit");

        String choice = readLine("> ");
        if (choice == null) {
            return;
        }

        switch (choice.trim()) {
            case "1" -> handleListItem();
            case "2" -> handleViewInventory();
            case "3" -> handleBrowseListings();
            case "4" -> {
                currentUser = null;
                System.out.println("Logged out.");
            }
            case "5" -> running = false;
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
        runServiceAction(() -> {
            List<Item> items = itemService.viewMyInventory(currentUser.id());
            if (items.isEmpty()) {
                System.out.println("Your inventory is empty.");
                return;
            }

            System.out.println("\n=== My Inventory ===");
            for (int i = 0; i < items.size(); i++) {
                printItem(i + 1, items.get(i), true);
            }
            System.out.println((items.size() + 1) + ") Back to menu");

            String input = readLine("Select an item to manage or go back: ");
            if (input == null) {
                return;
            }
            Integer choice = parseSelection(input);
            if (choice == null) {
                return;
            }
            if (choice == items.size() + 1) {
                return;
            }
            if (choice < 1 || choice > items.size()) {
                System.out.println("Invalid selection.");
                return;
            }
            handleManageItem(items.get(choice - 1));
        });
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
        int currentPage = 0;
        while (true) {
            final List<Item> availableItems;
            try {
                availableItems =
                        itemService.browseAvailableItems(currentUser.id());
            } catch (RuntimeException exception) {
                reportServiceError(exception);
                return;
            }

            if (availableItems.isEmpty()) {
                System.out.println("No items available to browse.");
                return;
            }

            int totalPages = (availableItems.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            currentPage = Math.min(currentPage, totalPages - 1);
            int startIndex = currentPage * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, availableItems.size());
            List<Item> pageItems = availableItems.subList(startIndex, endIndex);

            System.out.println("\n=== Listings (page "
                    + (currentPage + 1) + " of " + totalPages + ") ===");
            for (int i = 0; i < pageItems.size(); i++) {
                printItem(i + 1, pageItems.get(i), false);
            }

            int nextOption = -1;
            int previousOption = -1;
            int menuOption = pageItems.size() + 1;
            if (currentPage < totalPages - 1) {
                nextOption = menuOption++;
                System.out.println(nextOption + ") Next page");
            }
            if (currentPage > 0) {
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
            } else if (selection >= 1 && selection <= pageItems.size()) {
                showDetailedView(pageItems.get(selection - 1));
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
        System.out.println("Owner ID: " + item.ownerId());
        System.out.println("Status: " + item.status());
        readLine("Press Enter to return to listings: ");
    }

    private void printItem(int index, Item item, boolean includeStatus) {
        String line = String.format(
                Locale.ROOT, "%d) %s | %.2f/day | owner #%d",
                index, item.name(), item.costPerDay(), item.ownerId());
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
