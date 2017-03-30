package co.smartreceipts.android.sync.widget.backups;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.widget.Toast;

import com.google.common.base.Preconditions;

import javax.inject.Inject;

import co.smartreceipts.android.R;
import co.smartreceipts.android.SmartReceiptsApplication;
import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.analytics.events.ErrorEvent;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.database.controllers.TableControllerManager;
import co.smartreceipts.android.persistence.database.tables.Table;
import co.smartreceipts.android.sync.model.RemoteBackupMetadata;
import co.smartreceipts.android.sync.network.NetworkManager;
import dagger.android.support.AndroidSupportInjection;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class ImportRemoteBackupWorkerProgressDialogFragment extends DialogFragment {

    private static final String ARG_BACKUP_METADATA = "arg_backup_metadata";
    private static final String ARG_OVERWRITE = "arg_overwrite";

    @Inject
    DatabaseHelper database;
    @Inject
    NetworkManager networkManager;
    @Inject
    Analytics analytics;
    @Inject
    TableControllerManager tableControllerManager;

    private RemoteBackupsDataCache remoteBackupsDataCache;
    private Subscription subscription;

    private RemoteBackupMetadata backupMetadata;
    private boolean overwrite;

    public static ImportRemoteBackupWorkerProgressDialogFragment newInstance(@NonNull RemoteBackupMetadata remoteBackupMetadata, boolean overwrite) {
        final ImportRemoteBackupWorkerProgressDialogFragment fragment = new ImportRemoteBackupWorkerProgressDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(ARG_BACKUP_METADATA, remoteBackupMetadata);
        args.putBoolean(ARG_OVERWRITE, overwrite);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);
        backupMetadata = getArguments().getParcelable(ARG_BACKUP_METADATA);
        overwrite = getArguments().getBoolean(ARG_OVERWRITE);
        Preconditions.checkNotNull(backupMetadata, "This class requires that a RemoteBackupMetadata instance be provided");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ProgressDialog dialog = new ProgressDialog(getActivity(), getTheme());
        dialog.setMessage(getString(R.string.progress_import));
        dialog.setIndeterminate(true);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        return dialog;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final SmartReceiptsApplication smartReceiptsApplication = ((SmartReceiptsApplication)getActivity().getApplication());
        remoteBackupsDataCache = new RemoteBackupsDataCache(getFragmentManager(), getContext(),
                smartReceiptsApplication.getBackupProvidersManager(),
                networkManager,
                database);
    }

    @Override
    public void onResume() {
        super.onResume();
        subscription = remoteBackupsDataCache.restoreBackup(backupMetadata, overwrite)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(@Nullable Boolean success) {
                        if (success != null && success) {
                            Toast.makeText(getActivity(), R.string.toast_import_complete, Toast.LENGTH_LONG).show();
                            for (final Table table : database.getTables()) {
                                table.clearCache();
                            }
                            tableControllerManager.getTripTableController().get();
                            getActivity().finishAffinity(); // TODO: Fix this hack (for the settings import)
                        } else {
                            Toast.makeText(getActivity(), getString(R.string.IMPORT_ERROR), Toast.LENGTH_LONG).show();
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        analytics.record(new ErrorEvent(ImportRemoteBackupWorkerProgressDialogFragment.this, throwable));
                        Toast.makeText(getActivity(), getString(R.string.IMPORT_ERROR), Toast.LENGTH_LONG).show();
                        remoteBackupsDataCache.removeCachedRestoreBackupFor(backupMetadata);
                        dismiss();
                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        remoteBackupsDataCache.removeCachedRestoreBackupFor(backupMetadata);
                        dismiss();
                    }
                });
    }

    @Override
    public void onPause() {
        subscription.unsubscribe();
        super.onPause();
    }
}
