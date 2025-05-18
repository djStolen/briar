package org.briarproject.briar.api.forum.voting;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.api.data.BdfList; // Corrected import
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.system.Clock; // If you need a clock for timestamps in group creation
import org.briarproject.briar.api.forum.Forum; // For constants like MAJOR_VERSION, SALT_LENGTH
import org.briarproject.briar.api.identity.AuthorManager; // <<<< New Dependency
import org.briarproject.nullsafety.NotNullByDefault;

import java.security.SecureRandom; // For salt generation

import javax.inject.Inject; // Standard for dependency injection

import static org.briarproject.briar.api.forum.voting.VoteCountingForumConstants.CLIENT_ID;
import static org.briarproject.briar.api.forum.voting.VoteCountingForumConstants.MAJOR_VERSION;

@NotNullByDefault
public class VoteCountingForumFactoryImpl implements VoteCountingForumFactory {

	/**
	 * The length of a forum's random salt in bytes.
	 */
	private static final int FORUM_SALT_LENGTH = 32;
	//private static final int CLIENT_ID = 47;
	//private static final int MAJOR_VERSION = 1;

	private final GroupFactory groupFactory;
	private final ClientHelper clientHelper; // Or your project's equivalent
	private final SecureRandom secureRandom;
	private final AuthorManager authorManager;    // <<<< New field
	// private final Clock clock; // If needed for group descriptor timestamping

	@Inject
	public VoteCountingForumFactoryImpl(
			GroupFactory groupFactory,
			ClientHelper clientHelper,
			SecureRandom secureRandom,
			AuthorManager authorManager // <<<< Injected AuthorManager
			/* Clock clock */) {
		this.groupFactory = groupFactory;
		this.clientHelper = clientHelper;
		this.secureRandom = secureRandom;
		this.authorManager = authorManager; // <<<< Store it
		// this.clock = clock;
	}

	@Override
	public VoteCountingForum createVoteCountingForum(String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Forum name cannot be null or empty.");
		}
		// Consider UTF-8 length validation similar to ForumFactoryImpl if not already handled
		// by underlying group creation or forum constructor.

		byte[] salt = new byte[FORUM_SALT_LENGTH]; // Assuming constant is accessible
		secureRandom.nextBytes(salt);
		return createVoteCountingForum(name.trim(), salt);
	}

	@Override
	public VoteCountingForum createVoteCountingForum(String name, byte[] salt) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Forum name cannot be null or empty.");
		}
		if (salt == null || salt.length != FORUM_SALT_LENGTH) {
			throw new IllegalArgumentException("Invalid salt provided.");
		}

		// Group Descriptor Creation Logic (similar to what ForumFactoryImpl would do)
		// This creates the unique identifier for the forum's underlying synchronization group.
		BdfList descriptorItems = BdfList.of(name, salt);
		// If your descriptor also includes a timestamp or other items, add them here.
		// For example: descriptorItems.add(clock.currentTimeMillis());

		byte[] descriptor;
		try {
			descriptor = clientHelper.toByteArray(descriptorItems);
		} catch (FormatException e) {
			// This typically shouldn't happen with controlled inputs like name and salt.
			// It indicates an issue with BDF serialization.
			throw new AssertionError("Failed to serialize forum descriptor: " + e.getMessage(), e);
		}

		// Create the Bramble Group
		// Ensure CLIENT_ID and MAJOR_VERSION are appropriate for your VoteCountingForum
		// They might be the same as for standard forums, or specific if it's a distinct component.
		Group group = groupFactory.createGroup(
				CLIENT_ID,
				MAJOR_VERSION,
				descriptor
		);

		// Create and return the VoteCountingForum, now passing the AuthorManager
		return new VoteCountingForum(group, name, salt, authorManager); // <<<< Pass AuthorManager
	}
}