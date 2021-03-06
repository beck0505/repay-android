package com.repay.android.frienddetails;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

import com.repay.android.Debt;
import com.repay.android.Friend;
import com.repay.android.R;
import com.repay.android.adddebt.EditDebtActivity;
import com.repay.android.database.DatabaseHandler;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Property of Matt Allen
 * mattallen092@gmail.com
 * http://mattallensoftware.co.uk/
 *
 * This software is distributed under the Apache v2.0 license and use
 * of the Repay name may not be used without explicit permission from the project owner.
 *
 */

public class DebtHistoryFragment extends Fragment implements AdapterView.OnItemLongClickListener, AmountUpdatedCallback {

	private static final String 		TAG = DebtHistoryFragment.class.getName();

	private DatabaseHandler				mDB;
	private Friend						mFriend;
	private DebtHistoryAdapter			mAdapter;
	private ListView					mList;
	private TextView					mNoDebtsMsg;
	private Context						mContext;
	private ProgressBar					mProgressBar;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		return inflater.inflate(R.layout.fragment_debthistory, container, false);
	}

	public static DebtHistoryFragment newInstance(String message){
		DebtHistoryFragment f = new DebtHistoryFragment();
		Bundle bd1 = new Bundle(1);
		bd1.putString(TAG, message);
		f.setArguments(bd1);
		return f;
	}

	@Override
	public void onStart(){
		super.onStart();
		mContext = getActivity();
		mDB = new DatabaseHandler(getActivity());
		mList = (ListView)getView().findViewById(R.id.fragment_debtHistory_list);
		mList.setOnItemLongClickListener(this);
		mFriend = ((FriendDetailsActivity) getActivity()).getFriend();
		mNoDebtsMsg = (TextView)getView().findViewById(R.id.fragment_debtHistory_noDebts);
		mProgressBar = (ProgressBar)getView().findViewById(R.id.fragment_debtHistory_progress);
		mProgressBar.setVisibility(ProgressBar.GONE);
		new GetDebtsFromDB().execute(mDB);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, final View view, int position, long id) {
		final Debt debt = (Debt)view.getTag();
		AlertDialog.Builder chooseDialog = new AlertDialog.Builder(getActivity());
		chooseDialog.setTitle("Edit Or Delete?");
		chooseDialog.setItems(R.array.fragment_friendoverview_chooseDialog_items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if(which==0){
					Bundle b = new Bundle();
					b.putInt(EditDebtActivity.DEBT_ID, debt.getDebtID());
					if (debt.getAmount().compareTo(BigDecimal.ZERO)<0){
						b.putBoolean(EditDebtActivity.IOWETHEM_FIELD, true);
					} else {
						b.putBoolean(EditDebtActivity.IOWETHEM_FIELD, false);
					}
					Intent i = new Intent(getActivity(), EditDebtActivity.class);
					i.putExtras(b);
					getActivity().startActivity(i);
				}
				else if(which==1){
					deleteDebt(debt);
				}
			}
		});
		chooseDialog.show();
		return true;
	}
	
	private void deleteDebt(final Debt debt){
		AlertDialog.Builder deleteDialog = new AlertDialog.Builder(getActivity());
		deleteDialog.setTitle(R.string.activity_friendoverview_deletedialog_title);
		deleteDialog.setMessage(R.string.activity_friendoverview_deletedialog_deleteDebtMessage);
		deleteDialog.setPositiveButton(R.string.activity_friendoverview_deletedialog_yes, new AlertDialog.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				deleteDebtFromDatabase(debt);
				Log.i(TAG, "Deleting debt from database");
				new GetDebtsFromDB().execute(mDB);

			}
		});

		deleteDialog.setNegativeButton(R.string.activity_friendoverview_deletedialog_no, new AlertDialog.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				Log.i(TAG, "Delete Debt dialog dismissed");
			}
		});
		deleteDialog.show();
	}

	@Override
	public void onDebtTotalUpdated(Friend friend) {
		((FriendDetailsActivity)getActivity()).onDebtTotalUpdated(friend);
	}

	private class GetDebtsFromDB extends AsyncTask<DatabaseHandler, Integer, ArrayList<Debt>> {

		/* 
		 * Hide the TextView and ListView, then show the ProgressBar to show feedback
		 * while the data is loaded from the database and presented by the ListView adapter
		 */
		@Override
		protected void onPreExecute() {
			mList.setAdapter(null);
			mList.setVisibility(ListView.GONE);
			mNoDebtsMsg.setVisibility(TextView.GONE);
			mProgressBar.setVisibility(ProgressBar.VISIBLE);
		}

		@Override
		protected ArrayList<Debt> doInBackground(DatabaseHandler... params) {
			try {
				ArrayList<Debt> debts = params[0].getDebtsByRepayID(mFriend.getRepayID());
				Collections.sort(debts);
				return debts;
			} catch (Throwable e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(ArrayList<Debt> result){
			mProgressBar.setVisibility(ProgressBar.GONE);
			if (result!=null) {
				mAdapter = new DebtHistoryAdapter(mContext,
						R.layout.fragment_debthistory_listitem, result);
				mList.setAdapter(mAdapter);
				mList.setVisibility(ListView.VISIBLE);
			} else {
				mNoDebtsMsg.setVisibility(TextView.VISIBLE);
			}
		}
	}

	/**
	 * Remove a debt from the database and update the amount stored
	 * in the friends table
	 * @param debtToDelete The debt that is to be removed
	 */
	public void deleteDebtFromDatabase(Debt debtToDelete){
		try{
			// Get latest info first
			mFriend = ((FriendDetailsActivity) getActivity()).getFriend();
			// Remove debt from the debts table of DB
			mDB.removeDebt(debtToDelete.getDebtID());
		} catch (Exception e){
			Log.e(TAG, e.getMessage());
			Toast.makeText(getActivity(), "Could not remove debt from database", Toast.LENGTH_SHORT).show();
		}
		try{	
			// Recalculate all the debts stored against this person's ID.
			// It's more accurate this way
			ArrayList<Debt> allDebts = mDB.getDebtsByRepayID(mFriend.getRepayID());
			BigDecimal newAmount = new BigDecimal("0");
			if(allDebts!=null && allDebts.size()!=0){
				for (int i=0;i<=allDebts.size()-1;i++){
					newAmount = newAmount.add(allDebts.get(i).getAmount());
				}
			}
			mFriend.setDebt(newAmount);
		} catch (Exception e){
			Log.e(TAG, e.getMessage());
			Log.e(TAG, "User probably deleted last debt in database, so size returns 0. Safe to continue");
			// You probably got here because there aren't any debts left, or this code hates you.
			// Either way set this user's total debts as 0
			mFriend.setDebt(new BigDecimal("0"));
		} finally {
			// Plug this new data into the matrix
			mDB.updateFriendRecord(mFriend);
			// Trigger callback to activity
			onDebtTotalUpdated(mFriend);
		}
	}
}
