import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LuckyPickDesktop extends JFrame {
    private final Color bg = new Color(17, 19, 22);
    private final Color panel = new Color(35, 37, 42);
    private final Color panel2 = new Color(47, 50, 56);
    private final Color text = new Color(248, 244, 234);
    private final Color muted = new Color(185, 180, 169);
    private final Color gold = new Color(245, 200, 75);
    private final Color green = new Color(103, 211, 155);
    private final Color red = new Color(255, 120, 134);
    private final Color blue = new Color(105, 169, 255);

    private final Set<Integer> fixed = new LinkedHashSet<>();
    private final Set<Integer> excluded = new LinkedHashSet<>();
    private final List<List<Integer>> currentGames = new ArrayList<>();
    private final List<List<Integer>> savedGames = new ArrayList<>();
    private final List<List<Integer>> recentGames = new ArrayList<>();
    private final Random random = new Random();

    private final JPanel quickGrid = new JPanel(new GridLayout(5, 9, 7, 7));
    private final JPanel resultList = stack();
    private final JPanel savedList = stack();
    private final JPanel statsList = stack();
    private final JLabel notice = label("번호 버튼은 고정, 제외, 해제 순서로 바뀝니다.", 13, muted, false);
    private final JLabel countLabel = label("5게임", 14, text, true);
    private final JLabel intervalLabel = label("3초 간격", 14, text, true);
    private final JSlider gameSlider = new JSlider(1, 5, 5);
    private final JSlider intervalSlider = new JSlider(2, 10, 3);
    private final JButton autoButton = actionButton("자동 생성 시작", panel2, text);
    private final Timer autoTimer;

    private int gameCount = 5;
    private int intervalSeconds = 3;
    private boolean autoGenerating = false;

    public LuckyPickDesktop() {
        super("럭키픽 PC 테스트");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1080, 760);
        setMinimumSize(new Dimension(920, 640));
        setLocationRelativeTo(null);

        autoTimer = new Timer(intervalSeconds * 1000, event -> generateGames());
        setContentPane(buildUi());
        generateGames();
    }

    private JComponent buildUi() {
        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBorder(new EmptyBorder(22, 22, 22, 22));
        root.setBackground(bg);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = label("럭키픽 PC 테스트", 32, text, true);
        JLabel sub = label("Android 앱과 같은 로직을 PC에서 빠르게 검증하는 화면입니다.", 14, muted, false);
        JPanel titleBox = stack();
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(5));
        titleBox.add(sub);
        header.add(titleBox, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        tabs.addTab("생성", generatorTab());
        tabs.addTab("보관함", scroll(savedList));
        tabs.addTab("통계", scroll(statsList));
        tabs.addTab("안내", aboutTab());
        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    private JComponent generatorTab() {
        JPanel page = new JPanel(new GridLayout(1, 2, 18, 18));
        page.setBackground(bg);

        JPanel controls = card();
        controls.add(sectionTitle("생성 조건"));
        controls.add(row("게임 수", countLabel));
        styleSlider(gameSlider);
        gameSlider.addChangeListener(event -> {
            gameCount = gameSlider.getValue();
            countLabel.setText(gameCount + "게임");
        });
        controls.add(gameSlider);
        controls.add(Box.createVerticalStrut(10));

        controls.add(row("자동 생성 간격", intervalLabel));
        styleSlider(intervalSlider);
        intervalSlider.addChangeListener(event -> {
            intervalSeconds = intervalSlider.getValue();
            intervalLabel.setText(intervalSeconds + "초 간격");
            if (autoGenerating) restartAuto();
        });
        controls.add(intervalSlider);
        controls.add(Box.createVerticalStrut(14));

        controls.add(label("빠른 번호 선택", 14, muted, false));
        quickGrid.setOpaque(false);
        renderQuickGrid();
        controls.add(quickGrid);
        controls.add(Box.createVerticalStrut(14));

        JButton generateButton = actionButton("번호 생성", green, Color.BLACK);
        generateButton.addActionListener(event -> generateGames());
        controls.add(generateButton);
        controls.add(Box.createVerticalStrut(8));

        autoButton.addActionListener(event -> toggleAuto());
        controls.add(autoButton);
        controls.add(Box.createVerticalStrut(12));
        controls.add(notice);

        JPanel results = card();
        results.add(sectionTitle("추천 조합"));
        results.add(scroll(resultList));

        page.add(controls);
        page.add(results);
        return page;
    }

    private JComponent aboutTab() {
        JTextArea area = new JTextArea(
            "이 화면은 PC 테스트용입니다.\n\n" +
            "확인할 수 있는 기능\n" +
            "- 번호 생성\n- 고정/제외 번호\n- 자동 생성 시작/중지\n- 저장/삭제/복사\n- 최근 생성 통계\n\n" +
            "구글플레이 업로드는 Android AAB 파일을 사용합니다."
        );
        area.setFont(new Font("Malgun Gothic", Font.PLAIN, 15));
        area.setForeground(text);
        area.setBackground(panel);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(24, 24, 24, 24));
        return area;
    }

    private void renderQuickGrid() {
        quickGrid.removeAll();
        for (int number = 1; number <= 45; number++) {
            JButton button = actionButton(String.valueOf(number), numberColor(number), fixed.contains(number) ? Color.BLACK : text);
            button.setPreferredSize(new Dimension(44, 34));
            int current = number;
            button.addActionListener(event -> {
                toggleNumber(current);
                renderQuickGrid();
            });
            quickGrid.add(button);
        }
        quickGrid.revalidate();
        quickGrid.repaint();
    }

    private Color numberColor(int number) {
        if (fixed.contains(number)) return gold;
        if (excluded.contains(number)) return red;
        return panel2;
    }

    private void toggleNumber(int number) {
        if (fixed.contains(number)) {
            fixed.remove(number);
            excluded.add(number);
        } else if (excluded.contains(number)) {
            excluded.remove(number);
        } else if (fixed.size() < 5) {
            fixed.add(number);
        } else {
            notice.setText("고정 번호는 5개까지만 선택할 수 있어요.");
        }
    }

    private void generateGames() {
        currentGames.clear();
        try {
            for (int i = 0; i < gameCount; i++) currentGames.add(makeGame());
            recentGames.addAll(0, currentGames);
            while (recentGames.size() > 60) recentGames.remove(recentGames.size() - 1);
            renderResults();
            renderStats();
            notice.setText("마음에 드는 조합은 저장하거나 복사할 수 있어요.");
        } catch (IllegalStateException error) {
            notice.setText(error.getMessage());
        }
    }

    private List<Integer> makeGame() {
        List<Integer> available = new ArrayList<>();
        for (int number = 1; number <= 45; number++) {
            if (!fixed.contains(number) && !excluded.contains(number)) available.add(number);
        }
        if (fixed.size() + available.size() < 6) {
            throw new IllegalStateException("선택 가능한 번호가 부족해요. 제외 번호를 줄여 주세요.");
        }
        Collections.shuffle(available, random);
        List<Integer> picked = new ArrayList<>(fixed);
        picked.addAll(available.subList(0, 6 - fixed.size()));
        Collections.sort(picked);
        return picked;
    }

    private void renderResults() {
        resultList.removeAll();
        for (int i = 0; i < currentGames.size(); i++) {
            resultList.add(gameCard((i + 1) + "게임", currentGames.get(i), true));
            resultList.add(Box.createVerticalStrut(10));
        }
        resultList.revalidate();
        resultList.repaint();
    }

    private void renderSaved() {
        savedList.removeAll();
        if (savedGames.isEmpty()) savedList.add(label("아직 저장한 조합이 없습니다.", 15, muted, false));
        for (List<Integer> game : savedGames) {
            savedList.add(gameCard("저장 조합", game, false));
            savedList.add(Box.createVerticalStrut(10));
        }
        savedList.revalidate();
        savedList.repaint();
    }

    private JPanel gameCard(String title, List<Integer> numbers, boolean canSave) {
        JPanel card = new JPanel(new BorderLayout(14, 10));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setBackground(new Color(28, 30, 34));

        JLabel heading = label(title, 14, muted, true);
        JPanel balls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        balls.setOpaque(false);
        for (Integer number : numbers) balls.add(ball(number));

        JPanel left = stack();
        left.add(heading);
        left.add(Box.createVerticalStrut(10));
        left.add(balls);
        card.add(left, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 1, 0, 8));
        actions.setOpaque(false);
        JButton save = actionButton(canSave ? "저장" : "삭제", panel2, text);
        save.addActionListener(event -> {
            if (canSave) {
                if (!containsGame(savedGames, numbers)) savedGames.add(new ArrayList<>(numbers));
            } else {
                savedGames.remove(numbers);
            }
            renderSaved();
        });
        JButton copy = actionButton("복사", panel2, text);
        copy.addActionListener(event -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection("럭키픽 추천 번호: " + join(numbers)), null));
        actions.add(save);
        actions.add(copy);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private JLabel ball(int number) {
        JLabel label = label(String.valueOf(number), 15, Color.BLACK, true);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(ballColor(number));
        label.setPreferredSize(new Dimension(42, 42));
        return label;
    }

    private Color ballColor(int number) {
        if (number <= 10) return gold;
        if (number <= 20) return blue;
        if (number <= 30) return red;
        if (number <= 40) return green;
        return new Color(214, 215, 221);
    }

    private void renderStats() {
        statsList.removeAll();
        int[] counts = new int[46];
        for (List<Integer> game : recentGames) for (int number : game) counts[number]++;
        for (int rank = 0; rank < 12; rank++) {
            int best = 1;
            for (int number = 2; number <= 45; number++) if (counts[number] > counts[best]) best = number;
            statsList.add(label(best + "번  " + counts[best] + "회", 16, text, true));
            statsList.add(Box.createVerticalStrut(8));
            counts[best] = -1;
        }
        statsList.revalidate();
        statsList.repaint();
    }

    private void toggleAuto() {
        autoGenerating = !autoGenerating;
        if (autoGenerating) {
            generateGames();
            restartAuto();
            autoButton.setText("자동 생성 중지");
            notice.setText(intervalSeconds + "초마다 자동 생성합니다.");
        } else {
            autoTimer.stop();
            autoButton.setText("자동 생성 시작");
            notice.setText("자동 생성을 중지했어요.");
        }
    }

    private void restartAuto() {
        autoTimer.stop();
        autoTimer.setDelay(intervalSeconds * 1000);
        autoTimer.start();
    }

    private boolean containsGame(List<List<Integer>> games, List<Integer> target) {
        for (List<Integer> game : games) if (game.equals(target)) return true;
        return false;
    }

    private String join(List<Integer> numbers) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(numbers.get(i));
        }
        return builder.toString();
    }

    private JPanel card() {
        JPanel panelView = stack();
        panelView.setBorder(new EmptyBorder(18, 18, 18, 18));
        panelView.setBackground(panel);
        return panelView;
    }

    private JPanel stack() {
        JPanel panelView = new JPanel();
        panelView.setLayout(new BoxLayout(panelView, BoxLayout.Y_AXIS));
        panelView.setOpaque(false);
        return panelView;
    }

    private JLabel sectionTitle(String value) {
        JLabel label = label(value, 24, text, true);
        label.setBorder(new EmptyBorder(0, 0, 16, 0));
        return label;
    }

    private JLabel label(String value, int size, Color color, boolean bold) {
        JLabel label = new JLabel(value);
        label.setFont(new Font("Malgun Gothic", bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private JPanel row(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel left = label(label, 14, muted, false);
        row.add(left, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        row.setBorder(new EmptyBorder(6, 0, 4, 0));
        return row;
    }

    private JButton actionButton(String value, Color background, Color foreground) {
        JButton button = new JButton(value);
        button.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        return button;
    }

    private void styleSlider(JSlider slider) {
        slider.setOpaque(false);
        slider.setForeground(gold);
        slider.setBackground(panel);
    }

    private JScrollPane scroll(JComponent component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(panel);
        return scroll;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LuckyPickDesktop().setVisible(true));
    }
}
