package co.smartreceipts.android.imports;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.io.File;

import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.analytics.events.ErrorEvent;
import co.smartreceipts.android.model.Attachment;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.model.factory.ReceiptBuilderFactory;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.persistence.database.controllers.impl.ReceiptTableController;
import co.smartreceipts.android.persistence.database.operations.DatabaseOperationMetadata;
import co.smartreceipts.android.settings.UserPreferenceManager;
import io.reactivex.Single;
import wb.android.storage.StorageManager;

public class AttachmentSendFileImporter {

    private static final String TAG = AttachmentSendFileImporter.class.getSimpleName();

    private final Context mContext;
    private final Trip mTrip;
    private final StorageManager mStorageManager;
    private final UserPreferenceManager mPreferences;
    private final ReceiptTableController mReceiptTableController;
    private final Analytics mAnalytics;

    public AttachmentSendFileImporter(@NonNull Context context, @NonNull Trip trip, @NonNull PersistenceManager persistenceManager,
                                      @NonNull ReceiptTableController receiptTableController, @NonNull Analytics analytics) {
        this(context, trip, persistenceManager.getStorageManager(), persistenceManager.getPreferenceManager(), receiptTableController, analytics);
    }

    public AttachmentSendFileImporter(@NonNull Context context, @NonNull Trip trip, @NonNull StorageManager storageManager,
                                      @NonNull UserPreferenceManager preferences, @NonNull ReceiptTableController receiptTableController,
                                      @NonNull Analytics analytics) {
        mContext = Preconditions.checkNotNull(context.getApplicationContext());
        mTrip = Preconditions.checkNotNull(trip);
        mStorageManager = Preconditions.checkNotNull(storageManager);
        mPreferences = Preconditions.checkNotNull(preferences);
        mReceiptTableController = Preconditions.checkNotNull(receiptTableController);
        mAnalytics = Preconditions.checkNotNull(analytics);
    }

    @NonNull
    public Single<File> importAttachment(@NonNull Attachment attachment, @NonNull final Receipt receipt) {
        Preconditions.checkNotNull(attachment);

        final FileImportProcessor importProcessor;
        if (attachment.isImage()) {
            importProcessor = new ImageImportProcessor(mTrip, mStorageManager, mPreferences, mContext);
        } else if (attachment.isPDF()) {
            importProcessor = new GenericFileImportProcessor(mTrip, mStorageManager, mContext);
        } else {
            importProcessor = new AutoFailImportProcessor();
        }

        return importProcessor.process(attachment.getUri())
                .doOnSuccess(file -> mReceiptTableController.update(receipt,
                        new ReceiptBuilderFactory(receipt).setFile(file).build(), new DatabaseOperationMetadata()))
                .doOnError(throwable -> mAnalytics.record(new ErrorEvent(AttachmentSendFileImporter.this, throwable)));
    }

}
