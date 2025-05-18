// In VoteCountingForum.java
package org.briarproject.briar.api.forum.voting;

import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager; // Needed to get AuthorInfo

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Assuming Forum is a class you can extend.
public class VoteCountingForum extends Forum {

	// Represents a single voting action in the history
	public static class VoteEntry {
		private final AuthorId authorId;
		private final String previousProposalId; // Null if first vote for this author
		private final String newProposalId;
		private final long timestamp;

		public VoteEntry(AuthorId authorId, @javax.annotation.Nullable String previousProposalId, String newProposalId, long timestamp) {
			this.authorId = Objects.requireNonNull(authorId);
			this.previousProposalId = previousProposalId; // Can be null
			this.newProposalId = Objects.requireNonNull(newProposalId);
			this.timestamp = timestamp;
		}

		public AuthorId getAuthorId() {
			return authorId;
		}

		@javax.annotation.Nullable
		public String getPreviousProposalId() {
			return previousProposalId;
		}

		public String getNewProposalId() {
			return newProposalId;
		}

		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public String toString() {
			return "VoteEntry{" +
					"authorId=" + authorId +
					", previousProposalId='" + previousProposalId + '\'' +
					", newProposalId='" + newProposalId + '\'' +
					", timestamp=" + timestamp +
					'}';
		}
	}

	private final AuthorManager authorManager; // To verify authors

	private final Map<String, Integer> currentVoteCounts; // ProposalID -> Current Vote Count
	private final Map<AuthorId, String> userCurrentVote; // AuthorId -> VotedProposalID (tracks current vote)
	private final List<VoteEntry> voteHistory;         // Chronological list of all vote actions

	// Constructor
	public VoteCountingForum(Group group, String name, byte[] salt, AuthorManager authorManager) {
		super(group, name, salt);
		this.authorManager = Objects.requireNonNull(authorManager);
		this.currentVoteCounts = new HashMap<>();
		this.userCurrentVote = new HashMap<>();
		this.voteHistory = new ArrayList<>();
	}

	// --- Proposal Management ---
	public void addProposal(String proposalId) {
		if (proposalId == null || proposalId.trim().isEmpty()) {
			throw new IllegalArgumentException("Proposal ID cannot be null or empty.");
		}
		currentVoteCounts.putIfAbsent(proposalId.trim(), 0);
	}

	public Map<String, Integer> getCurrentVoteCounts() {
		return Collections.unmodifiableMap(new HashMap<>(currentVoteCounts)); // Return a copy
	}

	// --- Vote Casting and Changing ---
	public boolean castOrChangeVote(AuthorId authorId, String proposalId) {
		Objects.requireNonNull(authorId, "AuthorId cannot be null.");
		Objects.requireNonNull(proposalId, "ProposalId cannot be null.");

		// 1. Verify Author
		AuthorInfo authorInfo;
		try {
			// This might involve a DB call, consider if this needs to be async
			// or if AuthorManager can provide cached/read-only transaction access.
			authorInfo = authorManager.getAuthorInfo(authorId);
		} catch (Exception e) { // Catch DbException or other exceptions from AuthorManager
			// Log the exception
			System.err.println("Could not retrieve author info: " + e.getMessage());
			return false; // Cannot verify author
		}

		if (authorInfo.getStatus() != AuthorInfo.Status.VERIFIED) {
			System.out.println("Author " + authorId + " is not verified. Status: " + authorInfo.getStatus());
			return false; // Only verified users can vote
		}

		// 2. Check if the proposal exists
		if (!currentVoteCounts.containsKey(proposalId)) {
			System.out.println("Proposal " + proposalId + " does not exist.");
			return false; // Proposal does not exist
		}

		long timestamp = System.currentTimeMillis();
		String previousVote = userCurrentVote.get(authorId);

		// 3. Update vote counts and history
		synchronized (this) { // Synchronize to protect shared state
			if (previousVote != null) {
				// User is changing their vote
				if (previousVote.equals(proposalId)) {
					System.out.println("Author " + authorId + " already voted for " + proposalId + ". No change.");
					return true; // No change, but considered successful
				}
				// Decrement count for the old proposal
				currentVoteCounts.computeIfPresent(previousVote, (k, v) -> (v > 0) ? v - 1 : 0);
			}

			// Increment count for the new proposal
			currentVoteCounts.put(proposalId, currentVoteCounts.getOrDefault(proposalId, 0) + 1);

			// Update the user's current vote
			userCurrentVote.put(authorId, proposalId);

			// Add to history
			voteHistory.add(new VoteEntry(authorId, previousVote, proposalId, timestamp));
		}
		System.out.println("Author " + authorId + " successfully voted/changed vote to " + proposalId);
		return true;
	}


	// --- History Access ---
	public List<VoteEntry> getVoteHistory() {
		// Return an unmodifiable copy to prevent external modification
		return Collections.unmodifiableList(new ArrayList<>(voteHistory));
	}

	// --- Utility to get current vote for a user (optional) ---
	@javax.annotation.Nullable
	public String getCurrentVoteForAuthor(AuthorId authorId) {
		return userCurrentVote.get(authorId);
	}

	public String getDescription() {
		// If Forum has no getDescription(), you can't call super.getDescription()
		// You'd have to construct the description differently or get the base part another way.
		// For example, if Forum has getName():
		//return getName() + " (This forum supports verified vote counting with history)";
		return "This forum supports verified vote counting with history";
		// Or if you want a default description part:
		// return "Vote Counting Forum: " + getName() + " (This forum supports verified vote counting with history)";
	}
}