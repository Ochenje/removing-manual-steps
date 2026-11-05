package tech.kood.rental.transport;

import org.junit.jupiter.api.Test;
import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.service.ItemService;
import tech.kood.rental.service.UserService;
import tech.kood.rental.security.PasswordHasher;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliHandlerTest {

    @Test
    void exitsCleanlyWhenInputEnds() {
        String output = run("");

        assertTrue(output.contains("Goodbye!"));
        assertTrue(output.contains("1) Log in"));
    }

    @Test
    void preservesPasswordWhitespaceDuringRegistrationAndLogin() {
        String output = run("2\nalice\n secret \n1\nalice\n secret \n5\n");

        assertTrue(output.contains("Registration successful!"));
        assertTrue(output.contains("Login successful! Welcome, alice."));
    }

    @Test
    void browsesListingsAndReturnsFromItemDetails() {
        TestItemRepository itemRepository = new TestItemRepository();
        itemRepository.items.put(1L, new Item(
                1L, 2L, "Camera", "Mirrorless camera", 19.95,
                ItemStatus.AVAILABLE));

        String output = run(
                "2\nalice\npassword\n1\nalice\npassword\n3\n1\n\n2\n5\n",
                itemRepository);

        assertTrue(output.contains("Camera | 19.95/day | owner #2"));
        assertTrue(output.contains("Description: Mirrorless camera"));
    }

    @Test
    void listsAndDelistsAnItemThroughInventory() {
        TestItemRepository itemRepository = new TestItemRepository();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try (Scanner scanner = new Scanner(
                "2\nalice\npassword\n1\nalice\npassword\n"
                        + "1\nDrill\nCordless drill\n12.50\n"
                        + "2\n1\n1\n5\n")) {
            new CliHandler(
                    createUserService(),
                    new ItemService(itemRepository),
                    scanner).start();
        } finally {
            System.setOut(originalOut);
        }

        assertEquals(ItemStatus.UNLISTED, itemRepository.items.get(1L).status());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("12.50/day"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Item delisted."));
    }

    private String run(String input) {
        return run(input, new TestItemRepository());
    }

    private String run(String input, TestItemRepository itemRepository) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try (Scanner scanner = new Scanner(input)) {
            new CliHandler(
                    createUserService(),
                    new ItemService(itemRepository),
                    scanner).start();
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private UserService createUserService() {
        return new UserService(new TestUserRepository(), new PasswordHasher());
    }

    private static final class TestUserRepository
            implements UserService.UserRepository {

        private final Map<String, UserService.UserCredential> users =
                new HashMap<>();
        private long nextId = 1;

        @Override
        public boolean existsByUsername(String username) {
            return users.containsKey(username);
        }

        @Override
        public Optional<UserService.UserCredential> findCredentialsByUsername(
                String username) {
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public long create(String username, String passwordHash) {
            long id = nextId++;
            users.put(username,
                    new UserService.UserCredential(id, username, passwordHash));
            return id;
        }
    }

    private static final class TestItemRepository
            implements ItemService.ItemRepository {

        private final Map<Long, Item> items = new HashMap<>();
        private long nextId = 1;

        @Override
        public Optional<Item> findById(long itemId) {
            return Optional.ofNullable(items.get(itemId));
        }

        @Override
        public Item save(Item item) {
            Item saved = item.id() == null
                    ? new Item(nextId++, item.ownerId(), item.name(),
                            item.description(), item.costPerDay(), item.status())
                    : item;
            items.put(saved.id(), saved);
            return saved;
        }

        @Override
        public List<Item> findByOwnerId(long ownerId) {
            return items.values().stream()
                    .filter(item -> item.ownerId() == ownerId)
                    .toList();
        }

        @Override
        public List<Item> findAvailableNotOwnedBy(long userId) {
            List<Item> available = new ArrayList<>();
            for (Item item : items.values()) {
                if (item.ownerId() != userId
                        && item.status() == ItemStatus.AVAILABLE) {
                    available.add(item);
                }
            }
            return available;
        }
    }
}
