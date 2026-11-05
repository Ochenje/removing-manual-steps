package tech.kood.rental.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.domain.Rental;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.ItemRepository;
import tech.kood.rental.repository.RentalRepository;
import tech.kood.rental.repository.UserRepository;
import tech.kood.rental.security.PasswordHasher;
import tech.kood.rental.service.ItemService;
import tech.kood.rental.service.RentalService;
import tech.kood.rental.service.UserService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void exitsCleanlyWhenInputEnds() throws Exception {
        Fixture fixture = createFixture();

        String output = run(fixture, "");

        assertTrue(output.contains("Goodbye!"));
        assertTrue(output.contains("1) Log in"));
    }

    @Test
    void preservesPasswordWhitespaceDuringRegistrationAndLogin()
            throws Exception {
        Fixture fixture = createFixture();

        String output = run(
                fixture,
                "2\nalice\n secret \n1\nalice\n secret \n6\n");

        assertTrue(output.contains("Registration successful!"));
        assertTrue(output.contains("Login successful! Welcome, alice."));
    }

    @Test
    void browsesListingsAndReturnsFromItemDetails() throws Exception {
        Fixture fixture = createFixture();
        long ownerId = fixture.userService().register("owner", "password");
        fixture.itemRepository().insert(new Item(
                null, ownerId, "Camera", "Mirrorless camera", 19.95,
                ItemStatus.AVAILABLE));

        String output = run(
                fixture,
                "2\nalice\npassword\n1\nalice\npassword\n"
                        + "3\n1\n2\n2\n6\n");

        assertTrue(output.contains("Camera | 19.95/day | owner owner"));
        assertTrue(output.contains("Description: Mirrorless camera"));
    }

    @Test
    void browsesListingsAcrossPages() throws Exception {
        Fixture fixture = createFixture();
        long firstOwner = fixture.userService().register("owner1", "password");
        long secondOwner = fixture.userService().register("owner2", "password");
        for (int i = 0; i < 6; i++) {
            long ownerId = i % 2 == 0 ? firstOwner : secondOwner;
            fixture.itemRepository().insert(new Item(
                    null, ownerId, "Item " + i, "Description", i + 1,
                    ItemStatus.AVAILABLE));
        }

        String output = run(
                fixture,
                "2\nalice\npassword\n1\nalice\npassword\n"
                        + "3\n6\n1\n2\n3\n6\n");

        assertTrue(output.contains("Listings (page 1 of 2)"));
        assertTrue(output.contains("Listings (page 2 of 2)"));
        assertTrue(output.contains("6) Next page"));
        assertTrue(output.contains("2) Previous page"));
    }

    @Test
    void viewsInventoryAcrossPages() throws Exception {
        Fixture fixture = createFixture();
        long ownerId = fixture.userService().register("alice", "password");
        for (int i = 0; i < 6; i++) {
            fixture.itemRepository().insert(new Item(
                    null, ownerId, "Owned item " + i, "Description", i + 1,
                    ItemStatus.AVAILABLE));
        }

        String output = run(fixture, "1\nalice\npassword\n2\n6\n3\n6\n");

        assertTrue(output.contains("My Inventory (page 1 of 2)"));
        assertTrue(output.contains("My Inventory (page 2 of 2)"));
    }

    @Test
    void listsAndDelistsAnItemThroughInventory() throws Exception {
        Fixture fixture = createFixture();

        String output = run(
                fixture,
                "2\nalice\npassword\n1\nalice\npassword\n"
                        + "1\nDrill\nCordless drill\n12.50\n"
                        + "2\n1\n1\n2\n6\n");

        Item item = fixture.itemRepository().listByOwner(
                fixture.userService().login("alice", "password").id(), 5, 0)
                .getFirst();
        assertEquals(ItemStatus.UNLISTED, item.status());
        assertTrue(output.contains("12.50/day"));
        assertTrue(output.contains("Item delisted."));
    }

    @Test
    void rentsAnotherUsersItemFromListings() throws Exception {
        Fixture fixture = createFixture();
        long ownerId = fixture.userService().register("owner", "password");
        Item listed = fixture.itemRepository().insert(new Item(
                null, ownerId, "Camera", "Mirrorless camera", 19.95,
                ItemStatus.AVAILABLE));

        String output = run(
                fixture,
                "2\nrenter\npassword\n1\nrenter\npassword\n"
                        + "3\n1\n1\n3\n6\n");

        assertTrue(output.contains("Item rented successfully."));
        Item rented = fixture.itemRepository()
                .findById(listed.id()).orElseThrow();
        assertEquals(ItemStatus.RENTED, rented.status());
        Rental rental = fixture.rentalRepository()
                .findActiveByItemId(listed.id()).orElseThrow();
        assertEquals(3, rental.days());
        assertEquals(
                fixture.userService().login("renter", "password").id(),
                rental.renterId());
    }

    @Test
    void ownerCanConfirmReturnFromTheCli() throws Exception {
        Fixture fixture = createFixture();
        long ownerId = fixture.userService().register("owner", "password");
        long renterId = fixture.userService().register("renter", "password");
        Item listed = fixture.itemRepository().insert(new Item(
                null, ownerId, "Camera", "Mirrorless camera", 19.95,
                ItemStatus.AVAILABLE));
        Rental activeRental =
                fixture.rentalService().rentItem(renterId, listed.id(), 2);

        String output = run(
                fixture,
                "1\nowner\npassword\n4\n1\n1\n6\n");

        assertTrue(output.contains("Return confirmed."));
        assertTrue(output.contains("Renter: renter"));
        assertTrue(output.contains("Rental started: "
                + activeRental.rentedAt().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        assertTrue(output.contains("Expected return: "
                + activeRental.rentedAt().plusDays(activeRental.days()).format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        assertEquals(
                ItemStatus.AVAILABLE,
                fixture.itemRepository().findById(listed.id())
                        .orElseThrow().status());
        Rental returned = fixture.rentalRepository()
                .findById(activeRental.id()).orElseThrow();
        assertNotNull(returned.returnedAt());
    }

    @Test
    void ownerReturnListPagesAfterFiveItems() throws Exception {
        Fixture fixture = createFixture();
        long ownerId = fixture.userService().register("owner", "password");
        long renterId = fixture.userService().register("renter", "password");
        for (int i = 0; i < 6; i++) {
            Item listed = fixture.itemRepository().insert(new Item(
                    null, ownerId, "Camera " + i, "Camera", 10,
                    ItemStatus.AVAILABLE));
            fixture.rentalService().rentItem(renterId, listed.id(), 2);
        }

        String output = run(fixture, "1\nowner\npassword\n4\n6\n1\n2\n3\n6\n");

        assertTrue(output.contains("Items Awaiting Return (page 1 of 2)"));
        assertTrue(output.contains("Items Awaiting Return (page 2 of 2)"));
        assertTrue(output.contains("Renter: renter"));
    }

    private String run(Fixture fixture, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try (Scanner scanner = new Scanner(input)) {
            new CliHandler(
                    fixture.userService(),
                    fixture.itemService(),
                    fixture.rentalService(),
                    scanner).start();
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private Fixture createFixture() throws Exception {
        DatabaseConnection database =
                new DatabaseConnection(tempDir.resolve("app.db").toString());
        database.initialize();

        UserRepository userRepository = new UserRepository(database);
        ItemRepository itemRepository = new ItemRepository(database);
        RentalRepository rentalRepository = new RentalRepository(database);

        UserService userService =
                new UserService(userRepository, new PasswordHasher());
        ItemService itemService = new ItemService(itemRepository);
        RentalService rentalService =
                new RentalService(database, itemRepository, rentalRepository);

        return new Fixture(
                userService, itemService, rentalService,
                itemRepository, rentalRepository);
    }

    private record Fixture(
            UserService userService,
            ItemService itemService,
            RentalService rentalService,
            ItemRepository itemRepository,
            RentalRepository rentalRepository) {
    }
}
