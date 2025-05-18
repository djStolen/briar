package org.briarproject.briar.android.forum;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

import org.briarproject.briar.api.forum.voting.VoteCountingForumFactoryImpl;
import org.briarproject.briar.api.forum.voting.VoteCountingForumFactory;

@Module
public abstract class ForumModule {

    @Binds
    abstract VoteCountingForumFactory bindVoteCountingForumFactory(
            VoteCountingForumFactoryImpl impl
    );

    @Binds
    @IntoMap
    @ViewModelKey(ForumListViewModel.class)
    abstract ViewModel bindForumListViewModel(ForumListViewModel forumListViewModel);

    @Binds
    @IntoMap
    @ViewModelKey(ForumViewModel.class)
    abstract ViewModel bindForumViewModel(ForumViewModel forumViewModel);
}