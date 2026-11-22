package com.wechatarchive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.ArchiveEntry;
import com.wechatarchive.data.ArchiveSettings;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.domain.AttachmentDescriptor;
import com.wechatarchive.domain.CapturedMessage;
import com.wechatarchive.domain.ConversationWhitelistParser;
import com.wechatarchive.domain.ExportFileName;
import com.wechatarchive.domain.MarkdownExporter;
import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;
import com.wechatarchive.export.AttachmentContentProvider;
import com.wechatarchive.export.ShareFileStore;
import com.wechatarchive.importing.AttachmentStore;
import com.wechatarchive.importing.MoectZipImporter;
import com.wechatarchive.service.NotificationAccess;
import com.wechatarchive.service.WeChatNotificationListener;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int REQUEST_SAVE_MARKDOWN = 3101;
    private static final int REQUEST_IMPORT_MOECT = 3102;
    private static final DateTimeFormatter LIST_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger queryVersion = new AtomicInteger();

    private ArchiveDatabase database;
    private ArchiveSettings settings;
    private AttachmentStore attachmentStore;
    private MessageAdapter adapter;
    private TextView statusText;
    private TextView countText;
    private TextView footerSummary;
    private TextView emptyView;
    private Button accessButton;
    private EditText searchInput;
    private BroadcastReceiver archiveReceiver;
    private String pendingSaveMarkdown;
    private String pendingSaveFileName;
    private List<ArchiveEntry> visibleEntries = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new ArchiveDatabase(this);
        settings = new ArchiveSettings(this);
        attachmentStore = new AttachmentStore(this);
        archiveReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshData();
            }
        };
        buildUi();
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(WeChatNotificationListener.ACTION_ARCHIVE_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(archiveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            // The flags overload does not exist before API 33; the Android U
            // requirement only applies to the guarded branch above.
            registerReceiver(archiveReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessState();
        refreshData();
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(archiveReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was already unregistered during an interrupted lifecycle.
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        database.close();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.surface));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(18), dp(22), dp(18));
        header.setBackgroundColor(getColor(R.color.brand_dark));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(text("微信聊天归档器", 26, Color.WHITE, Typeface.BOLD));

        TextView subtitle = text(
                "通知实时采集，Moect 历史导入；照片、视频、文档与表情本地保存",
                13,
                getColor(R.color.white_80),
                Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.topMargin = dp(5);
        header.addView(subtitle, subtitleParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusRowParams = wrap();
        statusRowParams.topMargin = dp(16);
        header.addView(statusRow, statusRowParams);

        statusText = text("通知访问未开启", 12, getColor(R.color.warning), Typeface.BOLD);
        statusText.setBackgroundResource(R.drawable.bg_status_warn);
        statusRow.addView(statusText);

        countText = text("已归档 0 条", 12, Color.WHITE, Typeface.NORMAL);
        countText.setPadding(dp(12), 0, 0, 0);
        statusRow.addView(countText);

        LinearLayout accessRow = new LinearLayout(this);
        accessRow.setOrientation(LinearLayout.HORIZONTAL);
        accessRow.setPadding(dp(16), dp(14), dp(16), dp(8));
        root.addView(accessRow);

        accessButton = actionButton("打开通知访问设置", true);
        accessButton.setOnClickListener(view ->
                startActivitySafely(NotificationAccess.settingsIntent()));
        accessRow.addView(accessButton, weighted(1, 0));

        Button settingsButton = actionButton("采集设置", false);
        settingsButton.setOnClickListener(view -> showSettingsDialog());
        accessRow.addView(settingsButton, weighted(1, dp(8)));

        Button clearButton = actionButton("清空", false);
        clearButton.setTextColor(getColor(R.color.danger));
        clearButton.setOnClickListener(view -> confirmClear());
        accessRow.addView(clearButton, weighted(0.48f, dp(8)));

        LinearLayout importRow = new LinearLayout(this);
        importRow.setOrientation(LinearLayout.HORIZONTAL);
        importRow.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(importRow);

        Button importButton = actionButton("导入 Moect ZIP", true);
        importButton.setOnClickListener(view -> confirmImportMoect());
        importRow.addView(importButton, weighted(1, 0));

        Button refreshButton = actionButton("刷新", false);
        refreshButton.setOnClickListener(view -> refreshData());
        importRow.addView(refreshButton, weighted(0.36f, dp(8)));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("搜索会话、发送者或消息内容");
        searchInput.setTextSize(14);
        searchInput.setTextColor(getColor(R.color.text_primary));
        searchInput.setHintTextColor(getColor(R.color.text_secondary));
        searchInput.setBackgroundResource(R.drawable.bg_input);
        searchInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(dp(16), 0, dp(16), dp(10));
        root.addView(searchInput, searchParams);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshData();
            }
        });

        TextView boundary = text(
                "通知采集只包含授权后可见内容。完整历史与媒体可在 Windows 使用 "
                        + "Moect 导出 ZIP 后导入；应用不会读取微信私有数据库。",
                12,
                getColor(R.color.text_secondary),
                Typeface.NORMAL);
        boundary.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams boundaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        boundaryParams.setMargins(dp(16), 0, dp(16), dp(10));
        root.addView(boundary, boundaryParams);

        ListView messageList = new ListView(this);
        messageList.setDivider(null);
        messageList.setDividerHeight(dp(10));
        messageList.setClipToPadding(false);
        messageList.setPadding(dp(16), dp(2), dp(16), dp(8));
        messageList.setSelector(android.R.color.transparent);
        adapter = new MessageAdapter();
        messageList.setAdapter(adapter);
        messageList.setOnItemClickListener(this::showMessageDetail);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        root.addView(messageList, listParams);

        emptyView = text(
                "还没有归档消息。\n开启通知访问后，新的微信通知会出现在这里。",
                14,
                getColor(R.color.text_secondary),
                Typeface.NORMAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(28), dp(24), dp(28), dp(24));
        root.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        messageList.setEmptyView(emptyView);

        LinearLayout exportBar = new LinearLayout(this);
        exportBar.setOrientation(LinearLayout.VERTICAL);
        exportBar.setPadding(dp(16), dp(6), dp(16), dp(14));
        exportBar.setBackgroundColor(getColor(R.color.surface));
        root.addView(exportBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        footerSummary = text(
                "Markdown 会在本机生成，不依赖电脑或云端。",
                11,
                getColor(R.color.text_secondary),
                Typeface.NORMAL);
        footerSummary.setPadding(0, 0, 0, dp(8));
        exportBar.addView(footerSummary);

        LinearLayout exportRow = new LinearLayout(this);
        exportRow.setOrientation(LinearLayout.HORIZONTAL);
        exportBar.addView(exportRow);

        Button saveButton = actionButton("保存 Markdown", true);
        saveButton.setOnClickListener(view -> generateMarkdown(this::saveMarkdown));
        exportRow.addView(saveButton, weighted(1, 0));

        Button shareButton = actionButton("分享 Markdown", false);
        shareButton.setOnClickListener(view -> generateMarkdown(this::shareMarkdown));
        exportRow.addView(shareButton, weighted(1, dp(8)));
    }

    private void updateAccessState() {
        boolean granted = NotificationAccess.isGranted(this);
        if (granted) {
            statusText.setText("通知访问已开启");
            statusText.setTextColor(getColor(R.color.brand_dark));
            statusText.setBackgroundResource(R.drawable.bg_status_ok);
            accessButton.setText("重新打开通知访问设置");
        } else {
            statusText.setText("通知访问未开启");
            statusText.setTextColor(getColor(R.color.warning));
            statusText.setBackgroundResource(R.drawable.bg_status_warn);
            accessButton.setText("打开通知访问设置");
        }
    }

    private void refreshData() {
        String search = searchInput == null ? "" : searchInput.getText().toString();
        int version = queryVersion.incrementAndGet();
        io.execute(() -> {
            int total = database.count();
            List<ArchiveEntry> entries = database.query(search, 500);
            runOnUiThread(() -> {
                if (version != queryVersion.get() || isFinishing()) {
                    return;
                }
                visibleEntries = new ArrayList<>(entries);
                adapter.notifyDataSetChanged();
                countText.setText(String.format(
                        Locale.CHINA,
                        "已归档 %d 条",
                        total));
                footerSummary.setText(search.trim().isEmpty()
                        ? "Markdown 会在本机生成，不依赖电脑或云端。"
                        : "当前搜索显示 " + entries.size() + " 条，导出仍包含全部记录。");
            });
        });
    }

    private void showSettingsDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(4));

        Switch captureSwitch = new Switch(this);
        captureSwitch.setText("自动采集微信通知");
        captureSwitch.setTextSize(15);
        captureSwitch.setChecked(settings.isCaptureEnabled());
        content.addView(captureSwitch);

        TextView explanation = text(
                "留空表示采集全部微信会话；填写联系人或群名时，只采集完全匹配的会话。"
                        + "多个名称用逗号或换行分隔。",
                13,
                getColor(R.color.text_secondary),
                Typeface.NORMAL);
        LinearLayout.LayoutParams explanationParams = wrap();
        explanationParams.topMargin = dp(14);
        content.addView(explanation, explanationParams);

        EditText whitelistInput = new EditText(this);
        whitelistInput.setHint("例如：项目群，辅导员");
        whitelistInput.setText(settings.getWhitelistText());
        whitelistInput.setTextSize(15);
        whitelistInput.setMinLines(3);
        whitelistInput.setGravity(Gravity.TOP | Gravity.START);
        whitelistInput.setBackgroundResource(R.drawable.bg_input);
        whitelistInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(10);
        content.addView(whitelistInput, inputParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("采集设置")
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    settings.setCaptureEnabled(captureSwitch.isChecked());
                    settings.setWhitelistText(whitelistInput.getText().toString());
                    int size = ConversationWhitelistParser
                            .parse(settings.getWhitelistText())
                            .size();
                    toast(size == 0
                            ? "设置已保存：采集全部微信会话"
                            : "设置已保存：仅采集 " + size + " 个会话");
                    refreshData();
                })
                .show();
    }

    private void confirmImportMoect() {
        new AlertDialog.Builder(this)
                .setTitle("导入 Moect 聊天归档")
                .setMessage(
                        "先在 Windows 微信中使用 Moect 导出未加密的 ZIP，"
                                + "再在这里选择该 ZIP。导入会把聊天和可取得的附件复制到本机私有目录；"
                                + "原 ZIP 不会被修改，也不会上传。")
                .setNegativeButton("取消", null)
                .setPositiveButton("选择 ZIP", (dialog, which) -> openMoectPicker())
                .show();
    }

    private void openMoectPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                });
        try {
            startActivityForResult(intent, REQUEST_IMPORT_MOECT);
        } catch (ActivityNotFoundException error) {
            toast("系统没有可用的文件选择界面");
        }
    }

    private void importMoect(Uri source) {
        toast("正在导入聊天与附件…");
        io.execute(() -> {
            try {
                MoectZipImporter importer = new MoectZipImporter(
                        this,
                        database,
                        attachmentStore);
                MoectZipImporter.ImportResult result = importer.importFrom(
                        () -> getContentResolver().openInputStream(source));
                runOnUiThread(() -> {
                    toast("已导入 "
                            + result.getMessageCount()
                            + " 条，附件已存储 "
                            + result.getAvailableAttachmentCount()
                            + " 个，未取得 "
                            + result.getUnavailableAttachmentCount()
                            + " 个");
                    refreshData();
                });
            } catch (Exception error) {
                String message = error.getMessage();
                runOnUiThread(() -> toast(
                        "导入失败："
                                + (message == null || message.trim().isEmpty()
                                ? error.getClass().getSimpleName()
                                : message)));
            }
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空全部归档？")
                .setMessage("此操作会删除手机上已经保存的全部微信通知记录，且无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", (dialog, which) -> io.execute(() -> {
                    List<String> attachmentPaths = database.attachmentLocalPaths();
                    int deleted = database.clear();
                    attachmentStore.deletePaths(attachmentPaths);
                    runOnUiThread(() -> {
                        toast("已删除 " + deleted + " 条记录");
                        refreshData();
                    });
                }))
                .show();
    }

    private void showMessageDetail(AdapterView<?> parent, View view, int position, long id) {
        ArchiveEntry entry = visibleEntries.get(position);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(4));

        TextView metadata = text(
                entry.getConversation()
                        + "\n"
                        + LIST_TIME.format(
                        Instant.ofEpochMilli(entry.getOccurredAtMillis())
                                .atZone(ZoneId.systemDefault()))
                        + " · "
                        + entry.getSender(),
                13,
                getColor(R.color.text_secondary),
                Typeface.NORMAL);
        content.addView(metadata);

        TextView body = text(
                entry.getBody(),
                16,
                getColor(R.color.text_primary),
                Typeface.NORMAL);
        body.setTextIsSelectable(true);
        body.setPadding(0, dp(16), 0, dp(8));
        content.addView(body);
        appendAttachmentRows(content, entry.getAttachments());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        new AlertDialog.Builder(this)
                .setTitle(entry.getTitle())
                .setView(scrollView)
                .setNegativeButton("删除", (dialog, which) -> io.execute(() -> {
                    List<String> paths = new ArrayList<>();
                    for (AttachmentEntry attachment : entry.getAttachments()) {
                        if (attachment.isAvailable()) {
                            paths.add(attachment.getLocalPath());
                        }
                    }
                    database.delete(entry.getId());
                    attachmentStore.deletePaths(paths);
                    runOnUiThread(() -> {
                        toast("记录已删除");
                        refreshData();
                    });
                }))
                .setPositiveButton("关闭", null)
                .show();
    }

    private void appendAttachmentRows(
            LinearLayout content,
            List<AttachmentEntry> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        TextView heading = text(
                "附件 · " + attachments.size() + " 个",
                13,
                getColor(R.color.text_secondary),
                Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = wrap();
        headingParams.topMargin = dp(10);
        content.addView(heading, headingParams);

        for (AttachmentEntry attachment : attachments) {
            TextView row = text(
                    kindLabel(attachment.getKind())
                            + " · "
                            + attachment.getDisplayName()
                            + "\n"
                            + statusLabel(attachment),
                    14,
                    attachment.isAvailable()
                            ? getColor(R.color.brand_dark)
                            : getColor(R.color.text_secondary),
                    Typeface.NORMAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setClickable(true);
            row.setOnClickListener(view -> {
                if (attachment.isAvailable()) {
                    viewAttachment(attachment);
                } else {
                    toast("此附件在导出时未取得可用文件");
                }
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(7);
            content.addView(row, rowParams);
        }
    }

    private void viewAttachment(AttachmentEntry attachment) {
        if (attachment.getKind() == AttachmentKind.IMAGE
                || attachment.getKind() == AttachmentKind.ANIMATED_STICKER) {
            showImagePreview(attachment);
        } else {
            openAttachmentExternally(attachment);
        }
    }

    private void showImagePreview(AttachmentEntry attachment) {
        File file;
        try {
            file = attachmentStore.resolve(attachment.getLocalPath());
        } catch (IOException error) {
            toast("附件文件不可用");
            return;
        }
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setPadding(dp(16), dp(12), dp(16), dp(12));
        image.setMinimumHeight(dp(120));
        if (!showAnimatedPreview(image, file)) {
            image.setImageBitmap(decodePreview(file));
        }
        new AlertDialog.Builder(this)
                .setTitle(attachment.getDisplayName())
                .setView(image)
                .setPositiveButton("关闭", null)
                .setNegativeButton("用其他应用打开", (dialog, which) ->
                        openAttachmentExternally(attachment))
                .show();
    }

    private boolean showAnimatedPreview(ImageView image, File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        try {
            Drawable drawable = ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(file));
            image.setImageDrawable(drawable);
            if (drawable instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) drawable).setRepeatCount(
                        AnimatedImageDrawable.REPEAT_INFINITE);
                ((AnimatedImageDrawable) drawable).start();
            }
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private Bitmap decodePreview(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 2048) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void openAttachmentExternally(AttachmentEntry attachment) {
        try {
            attachmentStore.resolve(attachment.getLocalPath());
            Uri uri = attachmentUri(attachment);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, attachment.getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(
                    attachment.getDisplayName(),
                    uri));
            startActivity(intent);
        } catch (IOException error) {
            toast("附件文件不可用");
        } catch (ActivityNotFoundException error) {
            toast("没有可打开此附件的应用");
        }
    }

    private Uri attachmentUri(AttachmentEntry attachment) {
        return new Uri.Builder()
                .scheme("content")
                .authority(getPackageName() + AttachmentContentProvider.AUTHORITY_SUFFIX)
                .appendPath(String.valueOf(attachment.getId()))
                .appendPath(attachment.getDisplayName())
                .build();
    }

    private String kindLabel(AttachmentKind kind) {
        switch (kind) {
            case IMAGE:
                return "图片";
            case VIDEO:
                return "视频";
            case AUDIO:
                return "语音/音频";
            case DOCUMENT:
                return "文档";
            case ANIMATED_STICKER:
                return "动画表情";
            default:
                return "附件";
        }
    }

    private String statusLabel(AttachmentEntry attachment) {
        if (attachment.isAvailable()) {
            return "已存储 · " + formatBytes(attachment.getSizeBytes());
        }
        switch (attachment.getStatus()) {
            case MISSING:
                return "导出时未取得文件";
            case ENCRYPTED:
                return "原文件仍为加密状态";
            case CORRUPT:
                return "文件校验失败";
            case SKIPPED:
                return "导出时已跳过";
            default:
                return "格式暂不支持";
        }
    }

    private String formatBytes(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024L * 1024L) {
            return String.format(Locale.CHINA, "%.1f KB", size / 1024.0);
        }
        if (size < 1024L * 1024L * 1024L) {
            return String.format(
                    Locale.CHINA,
                    "%.1f MB",
                    size / (1024.0 * 1024.0));
        }
        return String.format(
                Locale.CHINA,
                "%.1f GB",
                size / (1024.0 * 1024.0 * 1024.0));
    }

    private void generateMarkdown(GeneratedMarkdownCallback callback) {
        toast("正在生成 Markdown…");
        io.execute(() -> {
            List<CapturedMessage> messages = new ArrayList<>();
            Map<String, List<AttachmentDescriptor>> attachments =
                    new LinkedHashMap<>();
            for (ArchiveEntry entry : database.allForExport()) {
                messages.add(entry.toCapturedMessage());
                if (!entry.getAttachments().isEmpty()) {
                    List<AttachmentDescriptor> descriptors = new ArrayList<>();
                    for (AttachmentEntry attachment : entry.getAttachments()) {
                        descriptors.add(new AttachmentDescriptor(
                                attachment.getDisplayName(),
                                kindLabel(attachment.getKind()),
                                attachment.isAvailable()));
                    }
                    attachments.put(entry.getFingerprint(), descriptors);
                }
            }
            Instant now = Instant.now();
            ZoneId zoneId = ZoneId.systemDefault();
            String markdown = MarkdownExporter.render(
                    messages,
                    attachments,
                    now,
                    zoneId);
            String fileName = ExportFileName.create(now, zoneId);
            runOnUiThread(() -> callback.onGenerated(markdown, fileName, messages.size()));
        });
    }

    private void saveMarkdown(String markdown, String fileName, int count) {
        pendingSaveMarkdown = markdown;
        pendingSaveFileName = fileName;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/markdown");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            startActivityForResult(intent, REQUEST_SAVE_MARKDOWN);
        } catch (ActivityNotFoundException error) {
            toast("系统没有可用的文件保存界面");
        }
    }

    private void shareMarkdown(String markdown, String fileName, int count) {
        io.execute(() -> {
            try {
                Uri uri = ShareFileStore.write(this, markdown);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/markdown");
                intent.putExtra(Intent.EXTRA_SUBJECT, fileName);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                if (markdown.length() <= 100_000) {
                    intent.putExtra(Intent.EXTRA_TEXT, markdown);
                }
                intent.setClipData(ClipData.newUri(getContentResolver(), fileName, uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> {
                    try {
                        startActivity(Intent.createChooser(intent, "分享 Markdown"));
                    } catch (ActivityNotFoundException error) {
                        toast("没有可用的分享应用");
                    }
                });
            } catch (IOException error) {
                runOnUiThread(() -> toast("生成分享文件失败：" + error.getMessage()));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_MOECT) {
            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null) {
                importMoect(data.getData());
            }
            return;
        }
        if (requestCode != REQUEST_SAVE_MARKDOWN
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }
        Uri destination = data.getData();
        String markdown = pendingSaveMarkdown;
        String fileName = pendingSaveFileName;
        pendingSaveMarkdown = null;
        pendingSaveFileName = null;
        if (markdown == null) {
            toast("没有可保存的 Markdown 内容");
            return;
        }
        io.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) {
                    throw new IOException("无法打开目标文件");
                }
                output.write(markdown.getBytes(StandardCharsets.UTF_8));
                output.flush();
                runOnUiThread(() -> toast("已保存 " + fileName));
            } catch (IOException error) {
                runOnUiThread(() -> toast("保存失败：" + error.getMessage()));
            }
        });
    }

    private void startActivitySafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            toast("当前系统无法打开该设置页面");
        }
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : getColor(R.color.brand_dark));
        button.setBackgroundResource(
                primary ? R.drawable.bg_button_primary : R.drawable.bg_button_secondary);
        button.setMinHeight(dp(44));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private TextView text(String value, int sizeSp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sizeSp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setLineSpacing(0f, 1.18f);
        return textView;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted(float weight, int startMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                weight);
        params.setMarginStart(startMargin);
        return params;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private interface GeneratedMarkdownCallback {
        void onGenerated(String markdown, String fileName, int count);
    }

    private final class MessageAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return visibleEntries.size();
        }

        @Override
        public ArchiveEntry getItem(int position) {
            return visibleEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ArchiveEntry entry = getItem(position);
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);

            LinearLayout top = new LinearLayout(MainActivity.this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(top);

            TextView conversation = text(
                    entry.getConversation(),
                    16,
                    getColor(R.color.text_primary),
                    Typeface.BOLD);
            conversation.setSingleLine(true);
            conversation.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(conversation, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f));

            TextView time = text(
                    LIST_TIME.format(
                            Instant.ofEpochMilli(entry.getOccurredAtMillis())
                                    .atZone(ZoneId.systemDefault())),
                    11,
                    getColor(R.color.text_secondary),
                    Typeface.NORMAL);
            time.setGravity(Gravity.END);
            top.addView(time);

            TextView body = text(
                    entry.getBody(),
                    14,
                    getColor(R.color.text_primary),
                    Typeface.NORMAL);
            body.setMaxLines(4);
            body.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyParams.topMargin = dp(9);
            card.addView(body, bodyParams);

            LinearLayout bottom = new LinearLayout(MainActivity.this);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bottomParams.topMargin = dp(9);
            card.addView(bottom, bottomParams);

            TextView sender = text(
                    entry.getSender().isEmpty() ? "微信通知" : entry.getSender(),
                    11,
                    getColor(R.color.text_secondary),
                    Typeface.NORMAL);
            bottom.addView(sender, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f));

            String attachmentSummary = attachmentSummary(entry.getAttachments());
            if (!attachmentSummary.isEmpty()) {
                TextView attachmentBadge = text(
                        attachmentSummary,
                        10,
                        getColor(R.color.brand_dark),
                        Typeface.BOLD);
                attachmentBadge.setBackgroundResource(R.drawable.bg_status_ok);
                bottom.addView(attachmentBadge);
            }

            if (entry.isSummary()) {
                TextView summary = text(
                        "汇总通知",
                        10,
                        getColor(R.color.warning),
                        Typeface.BOLD);
                summary.setBackgroundResource(R.drawable.bg_status_warn);
                bottom.addView(summary);
            }
            return card;
        }
    }

    private String attachmentSummary(List<AttachmentEntry> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        int images = 0;
        int videos = 0;
        int documents = 0;
        int audio = 0;
        int stickers = 0;
        int unavailable = 0;
        for (AttachmentEntry attachment : attachments) {
            switch (attachment.getKind()) {
                case IMAGE:
                    images++;
                    break;
                case VIDEO:
                    videos++;
                    break;
                case DOCUMENT:
                    documents++;
                    break;
                case AUDIO:
                    audio++;
                    break;
                case ANIMATED_STICKER:
                    stickers++;
                    break;
                default:
                    break;
            }
            if (!attachment.isAvailable()) {
                unavailable++;
            }
        }
        List<String> parts = new ArrayList<>();
        if (images > 0) {
            parts.add("图 " + images);
        }
        if (videos > 0) {
            parts.add("视频 " + videos);
        }
        if (documents > 0) {
            parts.add("文件 " + documents);
        }
        if (audio > 0) {
            parts.add("语音 " + audio);
        }
        if (stickers > 0) {
            parts.add("表情 " + stickers);
        }
        if (unavailable > 0) {
            parts.add("缺失 " + unavailable);
        }
        return TextUtils.join(" · ", parts);
    }
}
