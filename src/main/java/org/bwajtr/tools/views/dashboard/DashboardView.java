package org.bwajtr.tools.views.dashboard;


import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.board.Board;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.PlotOptionsLine;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.theme.lumo.LumoUtility.*;
import org.apache.commons.fileupload.util.Streams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@PageTitle("Dashboard")
@Route(value = "dashboard")
@RouteAlias(value = "")
public class DashboardView extends VerticalLayout {

    private int packetsSent;
    private int packetsReceived;
    private int packetsLost;
    private int above200msEvents;
    private int above500msEvents;
    private double packetsLostPercentage;
    private final List<Number> pingTimes = new ArrayList<>();
    private final Div boardPlaceHolder = new Div();

    public DashboardView() {
        addClassName("dashboard-view");

        var headerLayout = new VerticalLayout();
        headerLayout.add(new H2("Network lag analysis"));
        headerLayout.add(createFileUploadPanel());
        add(headerLayout);

        add(boardPlaceHolder);
        recreateBoard();
    }

    private void recreateBoard() {
        boardPlaceHolder.removeAll();
        boardPlaceHolder.setWidthFull();
        Board board = new Board();
        board.addRow(createHighlight("Packets sent", String.valueOf(packetsSent), null),
                createHighlight("Packets lost", String.valueOf(packetsLost), packetsLostPercentage),
                createHighlight("Events above 200 ms", String.valueOf(above200msEvents), null),
                createHighlight("Events above 500 ms", String.valueOf(above500msEvents), null));
        board.addRow(createRoundTripTimesChart());
        boardPlaceHolder.add(board);
    }

    private Component createFileUploadPanel() {
        var layout = new VerticalLayout();
        layout.setPadding(false);

        var firstLine = new HorizontalLayout();
        firstLine.add("Capture the ping using the following command and upload the result file:");
        Element code = new Element("code");
        code.setText("ping -n 600 google.com > ping_output.log");
        code.getStyle().set("padding", "0 10px 0 10px");
        firstLine.getElement().appendChild(code);
        layout.add(firstLine);

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);

        upload.addSucceededListener(event -> {
            try {
                var uploadedFileContent = Streams.asString(buffer.getInputStream());
                parseFile(uploadedFileContent);
                recreateBoard();
                upload.clearFileList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        layout.add(upload);

        return layout;
    }

    private void parseFile(String uploadedFileContent) {
        var lines = uploadedFileContent.split("\n");

        packetsSent = 0;
        packetsLost = 0;
        packetsReceived = 0;
        packetsLostPercentage = 0.0;
        above200msEvents = 0;
        above500msEvents = 0;
        pingTimes.clear();

        var replyLineParser = Pattern.compile("time=(\\d+?)ms");

        for (String line : lines) {
            if (line.startsWith("Reply")) {
                final Matcher matcher = replyLineParser.matcher(line);
                if (matcher.find()) {
                    final int pingTime = Integer.parseInt(matcher.group(1));
                    pingTimes.add(pingTime);
                    if (pingTime > 200) above200msEvents++;
                    if (pingTime > 500) above500msEvents++;
                }
            }

            if (line.startsWith("    Packets: ")) {
                var packetsSummaryParser = Pattern.compile("Packets: Sent = (\\d+?), Received = (\\d+?), Lost = (\\d+?) \\((\\d+?\\.?\\d*?)% loss\\)");
                final Matcher matcher = packetsSummaryParser.matcher(line);
                if (matcher.find()) {
                    packetsSent = Integer.parseInt(matcher.group(1));
                    packetsReceived = Integer.parseInt(matcher.group(2));
                    packetsLost = Integer.parseInt(matcher.group(3));
                    packetsLostPercentage = Double.parseDouble(matcher.group(4));
                }
            }
        }

    }

    private Component createHighlight(String title, String value, Double percentage) {
        String theme = "badge";

        H2 h2 = new H2(title);
        h2.addClassNames(FontWeight.NORMAL, Margin.NONE, TextColor.SECONDARY, FontSize.XSMALL);

        Span span = new Span(value);
        span.addClassNames(FontWeight.SEMIBOLD, FontSize.XXXLARGE);

        VerticalLayout layout = new VerticalLayout(h2, span);
        if (percentage != null) {
            Span badge = new Span(new Span(percentage + " %"));
            badge.getElement().getThemeList().add(theme);
            layout.add(badge);
        }
        layout.addClassName(Padding.LARGE);
        layout.setPadding(false);
        layout.setSpacing(false);
        return layout;
    }

    private Component createRoundTripTimesChart() {
        HorizontalLayout header = createHeader("Round trip times", "milliseconds");

        // Chart
        Chart chart = new Chart(ChartType.AREA);
        Configuration conf = chart.getConfiguration();
        conf.getTooltip().setFollowPointer(true);
        conf.getTooltip().setPointFormat("Time: <b>{point.y} ms</b>");
        conf.getyAxis().setTitle("Time (ms)");

        PlotOptionsLine plotOptions = new PlotOptionsLine();
        conf.addPlotOptions(plotOptions);

        final ListSeries pings = new ListSeries("Pings");
        pings.setData(pingTimes);
        conf.addSeries(pings);


        // Add it all together
        VerticalLayout viewEvents = new VerticalLayout(header, chart);
        viewEvents.addClassName(Padding.LARGE);
        viewEvents.setPadding(false);
        viewEvents.setSpacing(false);
        viewEvents.getElement().getThemeList().add("spacing-l");
        return viewEvents;
    }

    @SuppressWarnings("SameParameterValue")
    private HorizontalLayout createHeader(String title, String subtitle) {
        H2 h2 = new H2(title);
        h2.addClassNames(FontSize.XLARGE, Margin.NONE);

        Span span = new Span(subtitle);
        span.addClassNames(TextColor.SECONDARY, FontSize.XSMALL);

        VerticalLayout column = new VerticalLayout(h2, span);
        column.setPadding(false);
        column.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(column);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setSpacing(false);
        header.setWidthFull();
        return header;
    }

}
