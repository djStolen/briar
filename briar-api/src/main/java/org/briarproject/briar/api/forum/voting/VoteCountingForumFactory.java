// Option A: Interface remains the same (AuthorManager is an internal detail of Impl)
// Recommended if AuthorManager is a global/easily injectable service for the Impl
package org.briarproject.briar.api.forum.voting;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface VoteCountingForumFactory {
	VoteCountingForum createVoteCountingForum(String name);
	VoteCountingForum createVoteCountingForum(String name, byte[] salt);
}