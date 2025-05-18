package org.briarproject.briar.android.forum;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup; // Added
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.GroupId; // Assuming Forum returns GroupId
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumManager;
// Import your VoteCountingForum and its factory
import org.briarproject.briar.api.forum.voting.VoteCountingForum;
import org.briarproject.briar.api.forum.voting.VoteCountingForumFactory;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.android.util.UiUtils.enterPressed;
import static org.briarproject.briar.android.util.UiUtils.hideSoftKeyboard;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CreateForumActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(CreateForumActivity.class.getName());

	// Enum to represent forum types
	private enum ForumType {
		STANDARD,
		VOTE_COUNTING
	}

	private TextInputLayout nameEntryLayout;
	private EditText nameEntry;
	private RadioGroup forumTypeRadioGroup; // Added
	private Button createForumButton;
	private ProgressBar progress;

	private ForumType selectedForumType = ForumType.STANDARD; // Default

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumManager forumManager;
	@Inject
	protected volatile VoteCountingForumFactory voteCountingForumFactory; // Added

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		// You'll need to update your layout file (e.g., activity_create_forum.xml)
		// to include a RadioGroup for forum type selection.
		setContentView(R.layout.activity_create_forum);

		nameEntryLayout = findViewById(R.id.createForumNameLayout);
		nameEntry = findViewById(R.id.createForumNameEntry);
		nameEntry.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int lengthBefore, int lengthAfter) {
				enableOrDisableCreateButton();
			}
			@Override
			public void afterTextChanged(Editable s) {}
		});
		nameEntry.setOnEditorActionListener((v, actionId, e) -> {
			if (actionId == IME_ACTION_DONE || enterPressed(actionId, e)) {
				createForum();
				return true;
			}
			return false;
		});

		// Initialize RadioGroup and set listener
		forumTypeRadioGroup = findViewById(R.id.forumTypeRadioGroup); // Ensure this ID exists in your XML
		forumTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
			if (checkedId == R.id.radioStandardForum) { // Ensure this ID exists in your XML
				selectedForumType = ForumType.STANDARD;
			} else if (checkedId == R.id.radioVoteCountingForum) { // Ensure this ID exists in your XML
				selectedForumType = ForumType.VOTE_COUNTING;
			}
		});
		// Set a default selection in the RadioGroup if needed, e.g.,
		// findViewById(R.id.radioStandardForum).setChecked(true);


		createForumButton = findViewById(R.id.createForumButton);
		createForumButton.setOnClickListener(v -> createForum());

		progress = findViewById(R.id.createForumProgressBar);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		// You'll need to ensure your Dagger/Hilt component can inject VoteCountingForumFactory
		component.inject(this);
	}

	private void enableOrDisableCreateButton() {
		if (createForumButton == null) return;
		createForumButton.setEnabled(validateName());
	}

	private boolean validateName() {
		String name = nameEntry.getText().toString().trim();
		if (name.isEmpty()) {
			nameEntryLayout.setError(getString(R.string.name_empty)); // Assuming you have this string resource
			return false;
		}
		int length = StringUtils.toUtf8(name).length;
		if (length > MAX_FORUM_NAME_LENGTH) {
			nameEntryLayout.setError(getString(R.string.name_too_long));
			return false;
		}
		nameEntryLayout.setError(null);
		return true;
	}

	private void createForum() {
		if (!validateName()) return;
		hideSoftKeyboard(nameEntry);
		createForumButton.setVisibility(GONE);
		nameEntry.setEnabled(false); // Disable input while processing
		forumTypeRadioGroup.setEnabled(false); // Disable type selection while processing
		progress.setVisibility(VISIBLE);

		storeForum(nameEntry.getText().toString().trim());
	}

	private void storeForum(String name) {
		runOnDbThread(() -> {
			try {
				long start = now();
				Forum createdForum; // Use the base Forum type for the variable

				if (selectedForumType == ForumType.VOTE_COUNTING) {
					// Assuming VoteCountingForumFactory.createVoteCountingForum(name) exists
					// and VoteCountingForum extends/is a Forum
					LOG.info("Creating VoteCountingForum: " + name);
					createdForum = voteCountingForumFactory.createVoteCountingForum(name);
				} else {
					LOG.info("Creating Standard Forum: " + name);
					createdForum = forumManager.addForum(name); // Or use a ForumFactory if you have one
				}

				logDuration(LOG, "Storing forum (" + selectedForumType.name() + ")", start);
				displayForum(createdForum);

			} catch (DbException e) {
				logException(LOG, WARNING, e);
				// Ensure UI is reset on failure
				runOnUiThread(() -> {
					progress.setVisibility(GONE);
					createForumButton.setVisibility(VISIBLE);
					nameEntry.setEnabled(true);
					forumTypeRadioGroup.setEnabled(true);
					Toast.makeText(CreateForumActivity.this, R.string.forum_creation_failed, LENGTH_LONG).show(); // Add this string
				});
			} catch (Exception e) { // Catch other potential exceptions from factory, etc.
				logException(LOG, WARNING, e);
				runOnUiThread(() -> {
					progress.setVisibility(GONE);
					createForumButton.setVisibility(VISIBLE);
					nameEntry.setEnabled(true);
					forumTypeRadioGroup.setEnabled(true);
					Toast.makeText(CreateForumActivity.this, R.string.forum_creation_failed_generic, LENGTH_LONG).show(); // Add this string
				});
			}
		});
	}

	private void displayForum(Forum f) { // Parameter remains Forum
		runOnUiThreadUnlessDestroyed(() -> {
			// The ForumActivity should be able to handle any Forum subtype
			// If ForumActivity needs to know the specific type for UI/behavior changes,
			// you might need to pass an extra identifier or it could use 'instanceof'
			Intent i = new Intent(CreateForumActivity.this, ForumActivity.class);
			// Assuming f.getId() returns something compatible with GroupId or just the bytes
			GroupId forumId = (f instanceof VoteCountingForum) ? ((VoteCountingForum) f).getGroup().getId() : f.getId();
			// Or more simply if Forum already has getId() that returns the GroupId consistently
			// GroupId forumId = f.getId();

			i.putExtra(GROUP_ID, forumId.getBytes()); // Ensure GROUP_ID is defined
			i.putExtra(GROUP_NAME, f.getName()); // Ensure GROUP_NAME is defined

			// Optionally, pass the type if ForumActivity needs to behave differently
			// i.putExtra("FORUM_TYPE", (f instanceof VoteCountingForum) ? "VOTE_COUNTING" : "STANDARD");

			startActivity(i);
			Toast.makeText(CreateForumActivity.this,
					R.string.forum_created_toast, LENGTH_LONG).show();
			supportFinishAfterTransition();
		});
	}

	// Make sure GROUP_ID and GROUP_NAME constants are defined in this class or accessible
	// Example:
	public static final String GROUP_ID = "GROUP_ID";
	public static final String GROUP_NAME = "GROUP_NAME";
}