package tech.kood.rental.transport;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.User;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.StructuredLogger;
import tech.kood.rental.service.RentalService;

import java.util.List;
import java.util.Scanner;

public class CliHandler {

    private final RentalService rentalService;
    private final Scanner scanner;
    private final User sessionUser; // Set when the user logs in successfully

    public CliHandler(RentalService rentalService, Scanner scanner, User sessionUser) {
        this.rentalService = rentalService;
        this.scanner = scanner;
        this.sessionUser = sessionUser;
    }

    public void handleBrowseListings() {
        int currentPage = 1;
        final int pageSize = 5; // Enforced layout rule by page 3 of the brief

        while (true) {
            // Fetch all items excluding the logged-in user's items via the service boundary
            List<Item> availableItems = rentalService.browseAvailableListings(sessionUser.id());
            
            int totalItems = availableItems.size();
            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            if (totalPages == 0) {
                totalPages = 1; // Show at least one empty page block
            }

            // Slice out the 5 items belonging to the current page frame safely
            int startIndex = (currentPage - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalItems);
            List<Item> pageItems = availableItems.subList(startIndex, endIndex);

            System.out.println("\n=== Listings ===");
            int optionIndex = 1;

            // 1. Print the data rows matching standard format: Index) Name Cost/day Owner
            for (Item item : pageItems) {
                String ownerName = rentalService.getOwnerName(item.ownerId());
                System.out.printf("%d) %-15s %d/day  %s\n", 
                        optionIndex, item.name(), item.costPerDay(), ownerName);
                optionIndex++;
            }

            // 2. Dynamically determine sequential navigation option indexes
            int nextOption = -1;
            int prevOption = -1;

            if (currentPage < totalPages) {
                nextOption = optionIndex;
                System.out.printf("%d) Next page\n", nextOption);
                optionIndex++;
            }

            if (currentPage > 1) {
                prevOption = optionIndex;
                System.out.printf("%d) Previous page\n", prevOption);
                optionIndex++;
            }

            int backToMenuOption = optionIndex;
            System.out.printf("%d) Back to menu\n", backToMenuOption);

            System.out.print("> ");
            String input = scanner.nextLine().trim();

            try {
                int choice = Integer.parseInt(input);

                // Handle item selection index mapping
                if (choice >= 1 && choice <= pageItems.size()) {
                    Item chosenItem = pageItems.get(choice - 1);
                    handleItemDetailView(chosenItem);
                } 
                // Handle "Next page" trigger
                else if (choice == nextOption && nextOption != -1) {
                    currentPage++;
                } 
                // Handle "Previous page" trigger
                else if (choice == prevOption && prevOption != -1) {
                    currentPage--;
                } 
                // Handle "Back to menu" trigger
                else if (choice == backToMenuOption) {
                    break;
                } else {
                    System.out.println("Invalid option index selection. Please try again.");
                }

            } catch (NumberFormatException e) {
                System.out.println("Invalid numeric entry input format.");
            } catch (ValidationException e) {
                // Intercept domain rules gracefully and show safe user message
                StructuredLogger.warn("validation.failed | context=cli_browse message=" + e.getMessage());
                System.out.println("\n[Error]: " + e.getMessage());
            } catch (Exception e) {
                StructuredLogger.error("cli.operation.failed | context=browse_listings", e);
                System.out.println("\nAn unexpected systemic error occurred. Returning to safety.");
                break;
            }
        }
    }

    private void handleItemDetailView(Item item) {
        System.out.println("\n--- Detailed Item View ---");
        System.out.println("Name:        " + item.name());
        System.out.println("Description: " + item.description());
        System.out.println("Cost / Day:  " + item.costPerDay());
        System.out.print("\nWould you like to rent this item? (y/n): ");
        String choice = scanner.nextLine().trim().toLowerCase();

        if (choice.equals("y")) {
            System.out.print("Enter numbers of rental days: ");
            String daysInput = scanner.nextLine().trim();
            
            try {
                int days = Integer.parseInt(daysInput);
                
                // Trigger core transaction workflow via service matrix rules
                rentalService.createRental(item.id(), sessionUser.id(), days);
                System.out.println("Success! Rental recorded safely.");
                
            } catch (NumberFormatException e) {
                System.out.println("Invalid duration value entered.");
            }
        }
    }
}
