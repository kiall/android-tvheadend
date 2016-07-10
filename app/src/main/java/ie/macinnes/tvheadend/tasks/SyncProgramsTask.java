/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package ie.macinnes.tvheadend.tasks;


import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.Program;
import ie.macinnes.tvheadend.model.ProgramList;

public class SyncProgramsTask extends AsyncTask<TVHClient.EventList, Void, Boolean> {
    public static final String TAG = SyncProgramsTask.class.getSimpleName();

    private final Context mContext;
    private final Channel mChannel;
    private final Account mAccount;

    private int mAdditions = 0;
    private int mUpdates = 0;
    private int mDeletions = 0;
    private int mNochange = 0;

    protected SyncProgramsTask(Context context, Channel channel, Account account) {
        mContext = context;
        mChannel = channel;
        mAccount = account;
    }

    @Override
    protected Boolean doInBackground(TVHClient.EventList... eventLists) {
        Log.d(TAG, "Starting SyncProgramsTask for channel: " + mChannel.toString());

        if (isCancelled()) {
            return false;
        }

        for (TVHClient.EventList eventList : eventLists) {
            if (isCancelled()) {
                return false;
            }

            ProgramList programList = ProgramList.fromClientEventList(eventList, mChannel.getId(), mAccount);

            // Update the programs in the DB
            updatePrograms(programList);
        }

        return true;
    }

    public void updatePrograms(ProgramList newProgramList) {
        Log.d(TAG, "Updating programs for channel: " + mChannel.toString() + ". Have " + newProgramList.size() + " events.");

        ProgramList oldProgramList = TvContractUtils.getPrograms(mContext, mChannel);

        int oldProgramsIndex = 0;
        int newProgramsIndex = 0;
        final int oldProgramsCount = oldProgramList.size();
        final int newProgramsCount = newProgramList.size();

        // Compare the new programs with old programs one by one and update/delete the old one
        // or insert new program if there is no matching program in the database.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        while (newProgramsIndex < newProgramsCount) {
            if (isCancelled()) {
                Log.d(TAG, "Cancelled programs sync for channel: " + mChannel.toString());
                return;
            }

            Program oldProgram = oldProgramsIndex < oldProgramsCount
                    ? oldProgramList.get(oldProgramsIndex) : null;
            Program newProgram = newProgramList.get(newProgramsIndex);

            boolean addNewProgram = false;
            if (oldProgram != null) {
                if (oldProgram.equals(newProgram)) {
                    // Exact match. No need to update. Move on to the next programs.
                    oldProgramsIndex++;
                    newProgramsIndex++;

                    mNochange++;
                } else if (programEventIdMatches(oldProgram, newProgram)) {
                    // Partial match. Update the old program with the new one.
                    // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                    // could be application specific settings which belong to the old program.
                    ops.add(ContentProviderOperation.newUpdate(
                            TvContract.buildProgramUri(oldProgram.getProgramId()))
                            .withValues(newProgram.toContentValues())
                            .build());
                    oldProgramsIndex++;
                    newProgramsIndex++;
                    mUpdates++;
                } else if (oldProgram.getEndTimeUtcMillis()
                        < newProgram.getEndTimeUtcMillis()) {
                    // No match. Remove the old program first to see if the next program in
                    // {@code oldPrograms} partially matches the new program.
                    ops.add(ContentProviderOperation.newDelete(
                            TvContract.buildProgramUri(oldProgram.getProgramId()))
                            .build());
                    oldProgramsIndex++;
                    mDeletions++;
                } else {
                    // No match. The new program does not match any of the old programs. Insert
                    // it as a new program.
                    addNewProgram = true;
                    newProgramsIndex++;
                }
            } else {
                // No old programs. Just insert new programs.
                addNewProgram = true;
                newProgramsIndex++;
            }

            if (addNewProgram) {
                ops.add(ContentProviderOperation
                        .newInsert(TvContract.Programs.CONTENT_URI)
                        .withValues(newProgram.toContentValues())
                        .build());
                mAdditions++;
            }

            // Throttle the batch operation not to cause TransactionTooLargeException.
            if (ops.size() > 100
                    || newProgramsIndex >= newProgramsCount) {
                try {
                    mContext.getContentResolver().applyBatch(Constants.CONTENT_AUTHORITY, ops);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "Failed to insert programs.", e);
                    return;
                }
                ops.clear();
            }
        }

        Log.d(TAG, "Finished updating programs for channel: " + mChannel.toString() + ". A:" + Integer.toString(mAdditions) + ", U:" + Integer.toString(mUpdates) + ", D:" + Integer.toString(mDeletions) + ", NC:" + Integer.toString(mNochange));
    }

    private static boolean programEventIdMatches(Program oldProgram, Program newProgram) {
        final String oldEventId = oldProgram.getInternalProviderData().getEventId();
        final String newEventId = newProgram.getInternalProviderData().getEventId();

        return oldEventId.equals(newEventId);
    }

}
