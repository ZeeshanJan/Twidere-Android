/*
 * Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.activity.support;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.mariotaku.querybuilder.Columns.Column;
import org.mariotaku.querybuilder.Expression;
import org.mariotaku.querybuilder.OrderBy;
import org.mariotaku.querybuilder.RawItemArray;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.activity.support.QuickSearchBarActivity.SuggestionItem;
import org.mariotaku.twidere.adapter.AccountsSpinnerAdapter;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.model.ParcelableAccount;
import org.mariotaku.twidere.model.ParcelableUser;
import org.mariotaku.twidere.model.ParcelableUser.CachedIndices;
import org.mariotaku.twidere.provider.TwidereDataStore.CachedUsers;
import org.mariotaku.twidere.provider.TwidereDataStore.SavedSearches;
import org.mariotaku.twidere.provider.TwidereDataStore.SearchHistory;
import org.mariotaku.twidere.util.ImageLoaderWrapper;
import org.mariotaku.twidere.util.ParseUtils;
import org.mariotaku.twidere.util.ThemeUtils;
import org.mariotaku.twidere.util.Utils;

import java.util.ArrayList;
import java.util.List;

import static org.mariotaku.twidere.util.UserColorNameUtils.getUserNickname;

/**
 * Created by mariotaku on 15/1/6.
 */
public class QuickSearchBarActivity extends BaseSupportActivity implements OnClickListener,
        OnEditorActionListener, LoaderCallbacks<List<SuggestionItem>>, TextWatcher, OnItemSelectedListener, OnItemClickListener {

    private Spinner mAccountSpinner;
    private EditText mSearchQuery;
    private View mSearchSubmit;
    private ListView mSuggestionsList;
    private SuggestionsAdapter mUsersSearchAdapter;

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final SuggestionItem item = mUsersSearchAdapter.getItem(position);
        item.onItemClick(this, position);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        getSupportLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public int getThemeResourceId() {
        return ThemeUtils.getQuickSearchBarThemeResource(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_search_bar);
        final List<ParcelableAccount> accounts = ParcelableAccount.getAccountsList(this, false);
        final AccountsSpinnerAdapter accountsSpinnerAdapter = new AccountsSpinnerAdapter(this, R.layout.spinner_item_account_icon);
        accountsSpinnerAdapter.setDropDownViewResource(R.layout.list_item_user);
        accountsSpinnerAdapter.addAll(accounts);
        mAccountSpinner.setAdapter(accountsSpinnerAdapter);
        mAccountSpinner.setOnItemSelectedListener(this);
        if (savedInstanceState == null) {
            final Intent intent = getIntent();
            final int index = accountsSpinnerAdapter.findItemPosition(intent.getLongExtra(EXTRA_ACCOUNT_ID, -1));
            if (index != -1) {
                mAccountSpinner.setSelection(index);
            }
        }
        mUsersSearchAdapter = new SuggestionsAdapter(this);
        mSuggestionsList.setAdapter(mUsersSearchAdapter);
        mSuggestionsList.setOnItemClickListener(this);
        mSearchSubmit.setOnClickListener(this);
        mSearchQuery.setOnEditorActionListener(this);
        mSearchQuery.addTextChangedListener(this);

        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWindowAttributes();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.search_submit: {
                doSearch();
                break;
            }
        }
    }

    @Override
    public void onSupportContentChanged() {
        super.onSupportContentChanged();
        mAccountSpinner = (Spinner) findViewById(R.id.account_spinner);
        mSearchQuery = (EditText) findViewById(R.id.search_query);
        mSearchSubmit = findViewById(R.id.search_submit);
        mSuggestionsList = (ListView) findViewById(R.id.suggestions_list);
    }

    @Override
    public Loader<List<SuggestionItem>> onCreateLoader(int id, Bundle args) {
        return new SuggestionsLoader(this, mAccountSpinner.getSelectedItemId(), mSearchQuery.getText().toString());
    }

    @Override
    public void onLoadFinished(Loader<List<SuggestionItem>> loader, List<SuggestionItem> data) {
        mUsersSearchAdapter.setData(data);
    }

    @Override
    public void onLoaderReset(Loader<List<SuggestionItem>> loader) {
        mUsersSearchAdapter.setData(null);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (event == null) return false;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_ENTER: {
                doSearch();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        getSupportLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private void doSearch() {
        if (isFinishing()) return;
        final String query = ParseUtils.parseString(mSearchQuery.getText());
        if (TextUtils.isEmpty(query)) return;
        final long accountId = mAccountSpinner.getSelectedItemId();
        Utils.openSearch(this, accountId, query);
        finish();
    }

    private long getAccountId() {
        return mAccountSpinner.getSelectedItemId();
    }

    private void updateWindowAttributes() {
        final Window window = getWindow();
        final WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        window.setAttributes(attributes);
    }

    static interface SuggestionItem {

        void bindView(SuggestionsAdapter adapter, View view, int position);

        int getItemLayoutResource();

        int getItemViewType();

        boolean isEnabled();

        void onItemClick(QuickSearchBarActivity activity, int position);

    }

    static class HeaderItem implements SuggestionItem {

        static final int ITEM_VIEW_TYPE = 1;

        @Override
        public void bindView(SuggestionsAdapter adapter, View view, int position) {

        }

        @Override
        public int getItemViewType() {
            return ITEM_VIEW_TYPE;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void onItemClick(QuickSearchBarActivity activity, int position) {

        }

        @Override
        public int getItemLayoutResource() {
            return 0;
        }


    }

    static class SearchHistoryItem extends BaseClickableItem {

        static final int ITEM_VIEW_TYPE = 1;
        private final String mQuery;

        public SearchHistoryItem(String query) {
            mQuery = query;
        }

        @Override
        public final int getItemLayoutResource() {
            return R.layout.list_item_suggestion_search;
        }

        @Override
        public int getItemViewType() {
            return ITEM_VIEW_TYPE;
        }

        @Override
        public void onItemClick(QuickSearchBarActivity activity, int position) {
            Utils.openSearch(activity, activity.getAccountId(), mQuery);
            activity.finish();
        }


        @Override
        public void bindView(SuggestionsAdapter adapter, View view, int position) {
            final ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
            final TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            text1.setText(mQuery);
            icon.setImageResource(R.drawable.ic_action_history);
            icon.setColorFilter(text1.getCurrentTextColor(), Mode.SRC_ATOP);
        }
    }

    static abstract class BaseClickableItem implements SuggestionItem {
        @Override
        public final boolean isEnabled() {
            return true;
        }

    }

    static class SavedSearchItem extends BaseClickableItem {

        static final int ITEM_VIEW_TYPE = 2;
        private final String mQuery;

        public SavedSearchItem(String query) {
            mQuery = query;
        }

        @Override
        public final int getItemLayoutResource() {
            return R.layout.list_item_suggestion_search;
        }

        @Override
        public int getItemViewType() {
            return ITEM_VIEW_TYPE;
        }

        @Override
        public void bindView(SuggestionsAdapter adapter, View view, int position) {
            final ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
            final TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            text1.setText(mQuery);
            icon.setImageResource(R.drawable.ic_action_save);
            icon.setColorFilter(text1.getCurrentTextColor(), Mode.SRC_ATOP);
        }


        @Override
        public void onItemClick(QuickSearchBarActivity activity, int position) {
            Utils.openSearch(activity, activity.getAccountId(), mQuery);
            activity.finish();
        }
    }

    static class UserSuggestionItem extends BaseClickableItem {

        static final int ITEM_VIEW_TYPE = 3;
        private final ParcelableUser mUser;

        public UserSuggestionItem(Cursor c, CachedIndices i, long accountId) {
            mUser = new ParcelableUser(c, i, accountId);
        }

        @Override
        public int getItemViewType() {
            return ITEM_VIEW_TYPE;
        }

        @Override
        public void onItemClick(QuickSearchBarActivity activity, int position) {
            Utils.openUserProfile(activity, mUser, null);
            activity.finish();
        }

        @Override
        public final int getItemLayoutResource() {
            return R.layout.list_item_suggestion_user;
        }

        @Override
        public void bindView(SuggestionsAdapter adapter, View view, int position) {
            final ParcelableUser user = mUser;
            final Context context = adapter.getContext();
            final ImageLoaderWrapper loader = adapter.getImageLoader();
            final ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
            final TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            final TextView text2 = (TextView) view.findViewById(android.R.id.text2);

            final String nick = getUserNickname(context, user.id);
            text1.setText(TextUtils.isEmpty(nick) ? user.name : adapter.isNicknameOnly() ? nick :
                    context.getString(R.string.name_with_nickname, user.name, nick));
            text2.setText("@" + user.screen_name);
            loader.displayProfileImage(icon, user.profile_image_url);

        }
    }


    public static class SuggestionsAdapter extends BaseAdapter {

        private final Context mContext;
        private final LayoutInflater mInflater;
        private final ImageLoaderWrapper mImageLoader;
        private final boolean mNicknameOnly;
        private List<SuggestionItem> mData;

        SuggestionsAdapter(Context context) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
            mImageLoader = TwidereApplication.getInstance(context).getImageLoaderWrapper();
            final SharedPreferences pref = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
            mNicknameOnly = pref.getBoolean(KEY_NICKNAME_ONLY, false);
        }

        public Context getContext() {
            return mContext;
        }

        @Override
        public int getCount() {
            if (mData == null) return 0;
            return mData.size();
        }

        @Override
        public SuggestionItem getItem(int position) {
            if (mData == null) return null;
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view;
            final SuggestionItem item = getItem(position);
            if (convertView == null) {
                view = mInflater.inflate(item.getItemLayoutResource(), parent, false);
            } else {
                view = convertView;
            }
            item.bindView(this, view, position);
            return view;
        }

        public ImageLoaderWrapper getImageLoader() {
            return mImageLoader;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }

        @Override
        public int getItemViewType(int position) {
            if (mData == null) return IGNORE_ITEM_VIEW_TYPE;
            return mData.get(position).getItemViewType();
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        public boolean isNicknameOnly() {
            return mNicknameOnly;
        }

        public void setData(List<SuggestionItem> data) {
            mData = data;
            notifyDataSetChanged();
        }
    }

    public static class SuggestionsLoader extends AsyncTaskLoader<List<SuggestionItem>> {

        private final long mAccountId;
        private final String mQuery;

        public SuggestionsLoader(Context context, long accountId, String query) {
            super(context);
            mAccountId = accountId;
            mQuery = query;
        }

        @Override
        public List<SuggestionItem> loadInBackground() {
            final boolean emptyQuery = TextUtils.isEmpty(mQuery);
            final Context context = getContext();
            final ContentResolver resolver = context.getContentResolver();
            final List<SuggestionItem> result = new ArrayList<>();
            final String[] historyProjection = {SearchHistory.QUERY};
            final Cursor historyCursor = resolver.query(SearchHistory.CONTENT_URI,
                    historyProjection, null, null, SearchHistory.DEFAULT_SORT_ORDER);
            for (int i = 0, j = Math.min(emptyQuery ? 3 : 2, historyCursor.getCount()); i < j; i++) {
                historyCursor.moveToPosition(i);
                result.add(new SearchHistoryItem(historyCursor.getString(0)));
            }
            historyCursor.close();
            if (!emptyQuery) {
                final String queryEscaped = mQuery.replace("_", "^_");
                final SharedPreferences nicknamePrefs = context.getSharedPreferences(
                        USER_NICKNAME_PREFERENCES_NAME, Context.MODE_PRIVATE);
                final long[] nicknameIds = Utils.getMatchedNicknameIds(ParseUtils.parseString(mQuery),
                        nicknamePrefs);
                final Expression selection = Expression.or(
                        Expression.likeRaw(new Column(CachedUsers.SCREEN_NAME), "?||'%'", "^"),
                        Expression.likeRaw(new Column(CachedUsers.NAME), "?||'%'", "^"),
                        Expression.in(new Column(CachedUsers.USER_ID), new RawItemArray(nicknameIds)));
                final String[] selectionArgs = new String[]{queryEscaped, queryEscaped};
                final OrderBy orderBy = new OrderBy(CachedUsers.LAST_SEEN + " DESC", "score DESC",
                        CachedUsers.SCREEN_NAME, CachedUsers.NAME);
                final Uri uri = Uri.withAppendedPath(CachedUsers.CONTENT_URI_WITH_SCORE, String.valueOf(mAccountId));
                final Cursor usersCursor = context.getContentResolver().query(uri,
                        CachedUsers.COLUMNS, selection != null ? selection.getSQL() : null,
                        selectionArgs, orderBy.getSQL());
                final CachedIndices usersIndices = new CachedIndices(usersCursor);
                for (int i = 0, j = Math.min(5, usersCursor.getCount()); i < j; i++) {
                    usersCursor.moveToPosition(i);
                    result.add(new UserSuggestionItem(usersCursor, usersIndices, mAccountId));
                }
                usersCursor.close();
            } else {
                final String[] savedSearchesProjection = {SavedSearches.QUERY};
                final Expression savedSearchesWhere = Expression.equals(SavedSearches.ACCOUNT_ID, mAccountId);
                final Cursor savedSearchesCursor = resolver.query(SavedSearches.CONTENT_URI,
                        savedSearchesProjection, savedSearchesWhere.getSQL(), null,
                        SavedSearches.DEFAULT_SORT_ORDER);
                savedSearchesCursor.moveToFirst();
                while (!savedSearchesCursor.isAfterLast()) {
                    result.add(new SavedSearchItem(savedSearchesCursor.getString(0)));
                    savedSearchesCursor.moveToNext();
                }
                savedSearchesCursor.close();
            }
            return result;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }
    }

}
