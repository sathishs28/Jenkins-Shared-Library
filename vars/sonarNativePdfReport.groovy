#!/usr/bin/env groovy

@Grab('com.github.librepdf:openpdf:1.3.30')
import com.lowagie.text.*
import com.lowagie.text.pdf.*
import java.awt.Color

/**
 * Native PDF SonarQube Report Generator (No HTML intermediate)
 * 
 * Usage:
 *   @Library('your-shared-lib') _
 *   sonarNativePdfReport(recipientEmail: 'team@company.com')
 */
def call(Map config = [:]) {
    
    def recipient   = config.recipientEmail ?: 'ovt.bangalore@gmail.com'
    def fromEmail   = config.fromEmail     ?: 'sathish.s@vimatch.in'
    def sonarHost   = config.sonarHost     ?: env.SONAR_HOST ?: 'http://192.168.0.5:9000'
    def projectKey  = config.projectKey    ?: env.SONAR_PROJECT_KEY ?: 'STB_Automation_Framework'
    
    // ─── Status & Colors ───
    def buildStatus = currentBuild.currentResult
    def statusText, headerColor, alertColor, alertMsg
    
    if (buildStatus == 'SUCCESS') {
        statusText  = 'PASSED'
        headerColor = new Color(40, 167, 69)    // #28a745
        alertColor  = new Color(212, 237, 218)  // #d4edda
        alertMsg    = 'All quality checks passed. No action required.'
    } else if (buildStatus == 'UNSTABLE') {
        statusText  = 'QUALITY GATE FAILED'
        headerColor = new Color(253, 126, 20)   // #fd7e14
        alertColor  = new Color(255, 243, 205)  // #fff3cd
        alertMsg    = 'The SonarQube Quality Gate did not pass. Please review the new issues.'
    } else {
        statusText  = 'BUILD FAILED'
        headerColor = new Color(220, 53, 69)    // #dc3545
        alertColor  = new Color(248, 215, 218)  // #f8d7da
        alertMsg    = 'The pipeline failed. Report shows the latest available scan data.'
    }
    
    def safe = { val, fallback = 'N/A' ->
        (val == null || val.toString().trim() == '') ? fallback : val.toString()
    }
    
    def totalNewIssues = 0
    try {
        totalNewIssues = safe(env.SONAR_NEW_BUGS, '0').toInteger() +
                         safe(env.SONAR_NEW_VULNERABILITIES, '0').toInteger() +
                         safe(env.SONAR_NEW_CODE_SMELLS, '0').toInteger() +
                         safe(env.SONAR_NEW_HOTSPOTS, '0').toInteger()
    } catch (e) { totalNewIssues = 0 }
    
    def pdfName = "sonar-report-${env.BUILD_NUMBER}.pdf"
    def out = new FileOutputStream(pdfName)
    
    // ─── Document Setup ───
    def doc = new Document(PageSize.A4, 36, 36, 50, 36)
    def writer = PdfWriter.getInstance(doc, out)
    doc.open()
    
    // Fonts
    def fontTitle    = new Font(Font.HELVETICA, 20, Font.BOLD, Color.WHITE)
    def fontBadge    = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE)
    def fontHeader   = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(44, 62, 80))
    def fontLabel    = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(73, 80, 87))
    def fontValue    = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(73, 80, 87))
    def fontBoldVal  = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(73, 80, 87))
    def fontAlert    = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(33, 37, 41))
    def fontMetric   = new Font(Font.HELVETICA, 22, Font.BOLD, Color.WHITE)
    def fontMetLabel = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE)
    def fontLink     = new Font(Font.HELVETICA, 9, Font.UNDERLINE, new Color(51, 122, 183))
    def fontFooter   = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(173, 181, 189))
    def fontRed      = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(220, 53, 69))
    def fontOrange   = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(253, 126, 20))
    def fontGreen    = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(40, 167, 69))
    def fontGray     = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(108, 117, 125))
    def fontCyan     = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(23, 162, 184))
    
    // ─── Helper: Horizontal Rule (replaces LineSeparator) ───
    def addHorizontalRule = { color ->
        def hrTable = new PdfPTable(1)
        hrTable.widthPercentage = 100
        def hrCell = new PdfPCell(new Phrase(" "))
        hrCell.border = Rectangle.BOTTOM
        hrCell.borderColor = color
        hrCell.borderWidthBottom = 1f
        hrCell.paddingTop = 0
        hrCell.paddingBottom = 4
        hrTable.addCell(hrCell)
        doc.add(hrTable)
    }
    
    // ─── Helper: Metric Box ───
    def metricCell = { value, label, bgColor ->
        def table = new PdfPTable(1)
        table.widthPercentage = 100
        def c1 = new PdfPCell(new Phrase(value, fontMetric))
        c1.backgroundColor = bgColor
        c1.horizontalAlignment = Element.ALIGN_CENTER
        c1.border = Rectangle.NO_BORDER
        c1.paddingTop = 12; c1.paddingBottom = 4
        def c2 = new PdfPCell(new Phrase(label, fontMetLabel))
        c2.backgroundColor = bgColor
        c2.horizontalAlignment = Element.ALIGN_CENTER
        c2.border = Rectangle.NO_BORDER
        c2.paddingTop = 2; c2.paddingBottom = 12
        table.addCell(c1)
        table.addCell(c2)
        def wrap = new PdfPCell(table)
        wrap.border = Rectangle.NO_BORDER
        wrap.padding = 4
        return wrap
    }
    
    // ─── HEADER ───
    def headerTable = new PdfPTable(1)
    headerTable.widthPercentage = 100
    def titleCell = new PdfPCell()
    titleCell.backgroundColor = headerColor
    titleCell.horizontalAlignment = Element.ALIGN_CENTER
    titleCell.paddingTop = 20; titleCell.paddingBottom = 20
    titleCell.border = Rectangle.NO_BORDER
    titleCell.addElement(new Paragraph("SonarQube Analysis Report", fontTitle))
    def badge = new Paragraph("${statusText}", fontBadge)
    badge.alignment = Element.ALIGN_CENTER
    titleCell.addElement(badge)
    headerTable.addCell(titleCell)
    doc.add(headerTable)
    doc.add(Chunk.NEWLINE)
    
    // ─── ALERT BOX ───
    def alertTable = new PdfPTable(1)
    alertTable.widthPercentage = 100
    def alertCell = new PdfPCell(new Phrase(alertMsg, fontAlert))
    alertCell.backgroundColor = alertColor
    alertCell.horizontalAlignment = Element.ALIGN_LEFT
    alertCell.verticalAlignment = Element.ALIGN_MIDDLE
    alertCell.paddingTop = 10; alertCell.paddingBottom = 10
    alertCell.paddingLeft = 12; alertCell.paddingRight = 12
    alertCell.border = Rectangle.NO_BORDER
    alertTable.addCell(alertCell)
    doc.add(alertTable)
    doc.add(Chunk.NEWLINE)
    
    if (totalNewIssues > 0) {
        def warnTable = new PdfPTable(1)
        warnTable.widthPercentage = 100
        def warnFont = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(133, 100, 4))
        def warnCell = new PdfPCell(new Phrase("New Issues Detected: ${totalNewIssues} new issue(s) found in this commit. Please review before merging.", warnFont))
        warnCell.backgroundColor = new Color(255, 243, 205)
        warnCell.border = Rectangle.BOX
        warnCell.borderColor = new Color(253, 126, 20)
        warnCell.borderWidth = 1.5f
        warnCell.paddingTop = 10; warnCell.paddingBottom = 10
        warnCell.paddingLeft = 12; warnCell.paddingRight = 12
        warnTable.addCell(warnCell)
        doc.add(warnTable)
        doc.add(Chunk.NEWLINE)
    }
    
    // ─── SECTION HELPER ───
    def addSection = { title ->
        def p = new Paragraph(title, fontHeader)
        p.spacingBefore = 14
        p.spacingAfter = 6
        doc.add(p)
        addHorizontalRule(new Color(233, 236, 239))
    }
    
    // ─── BUILD INFO ───
    addSection("Build Information")
    def infoTable = new PdfPTable([30, 70] as float[])
    infoTable.widthPercentage = 100
    def infoRows = [
        ['Project',         safe(env.JOB_NAME)],
        ['Build Number',    "#${safe(env.BUILD_NUMBER)}"],
        ['Commit',          safe(env.GIT_COMMIT)?.take(7) ?: 'N/A'],
        ['Branch',          safe(env.GIT_BRANCH, 'main')],
        ['Build Status',    statusText],
        ['Quality Gate',    safe(env.SONAR_STATUS, 'UNKNOWN')],
        ['Duration',        safe(currentBuild.durationString)]
    ]
    infoRows.each { row ->
        def c1 = new PdfPCell(new Phrase(row[0], fontLabel))
        c1.border = Rectangle.BOTTOM; c1.borderColor = new Color(233, 236, 239)
        c1.paddingTop = 6; c1.paddingBottom = 6
        def c2 = new PdfPCell(new Phrase(row[1], fontBoldVal))
        c2.border = Rectangle.BOTTOM; c2.borderColor = new Color(233, 236, 239)
        c2.paddingTop = 6; c2.paddingBottom = 6
        infoTable.addCell(c1)
        infoTable.addCell(c2)
    }
    doc.add(infoTable)
    doc.add(Chunk.NEWLINE)
    
    // ─── NEW ISSUES ───
    addSection("New Issues (Introduced in This Commit)")
    def newTable = new PdfPTable(4)
    newTable.widthPercentage = 100
    def newIssues = [
        [safe(env.SONAR_NEW_BUGS, '0'),            'New Bugs',            new Color(253, 126, 20)],
        [safe(env.SONAR_NEW_VULNERABILITIES, '0'), 'New Vulnerabilities', new Color(253, 126, 20)],
        [safe(env.SONAR_NEW_CODE_SMELLS, '0'),     'New Code Smells',     new Color(253, 126, 20)],
        [safe(env.SONAR_NEW_HOTSPOTS, '0'),        'New Hotspots',        new Color(253, 126, 20)]
    ]
    newIssues.each { m ->
        newTable.addCell(metricCell(m[0], m[1], m[2]))
    }
    doc.add(newTable)
    doc.add(Chunk.NEWLINE)
    
    // ─── FINAL SUMMARY ───
    addSection("Final Summary")
    def sumTable = new PdfPTable([40, 60] as float[])
    sumTable.widthPercentage = 100
    def sumRows = [
        ['Quality Gate',        safe(env.SONAR_STATUS, 'UNKNOWN')],
        ['Coverage',            "${safe(env.SONAR_COVERAGE, '0.0')}%"],
        ['Code Smells',         safe(env.SONAR_CODE_SMELLS, '0')],
        ['Bugs',                safe(env.SONAR_BUGS, '0')],
        ['Vulnerabilities',     safe(env.SONAR_VULNERABILITIES, '0')],
        ['Hotspots',            safe(env.SONAR_HOTSPOTS, '0')],
        ['LOC',                 safe(env.SONAR_LINES, '0')],
        ['Duplication',         "${safe(env.SONAR_DUPLICATION, '0.0')}%"],
        ['New Bugs',            safe(env.SONAR_NEW_BUGS, '0')],
        ['New Vulnerabilities', safe(env.SONAR_NEW_VULNERABILITIES, '0')],
        ['New Code Smells',     safe(env.SONAR_NEW_CODE_SMELLS, '0')],
        ['New Hotspots',        safe(env.SONAR_NEW_HOTSPOTS, '0')],
        ['New Coverage',        "${safe(env.SONAR_NEW_COVERAGE, '0.0')}%"]
    ]
    sumRows.each { row ->
        def c1 = new PdfPCell(new Phrase(row[0], fontLabel))
        c1.border = Rectangle.BOTTOM; c1.borderColor = new Color(233, 236, 239)
        c1.paddingTop = 6; c1.paddingBottom = 6
        def c2 = new PdfPCell(new Phrase(row[1], fontBoldVal))
        c2.border = Rectangle.BOTTOM; c2.borderColor = new Color(233, 236, 239)
        c2.paddingTop = 6; c2.paddingBottom = 6
        sumTable.addCell(c1); sumTable.addCell(c2)
    }
    doc.add(sumTable)
    doc.add(Chunk.NEWLINE)
    
    // ─── OVERALL METRICS ───
    addSection("Overall Code Quality Metrics")
    def metTable = new PdfPTable(4)
    metTable.widthPercentage = 100
    def metrics = [
        [safe(env.SONAR_BUGS, '0'),             'Total Bugs',          new Color(220, 53, 69)],
        [safe(env.SONAR_VULNERABILITIES, '0'),  'Total Vulnerabilities', new Color(220, 53, 69)],
        [safe(env.SONAR_CODE_SMELLS, '0'),      'Total Code Smells',   new Color(253, 126, 20)],
        ["${safe(env.SONAR_COVERAGE, '0.0')}%", 'Coverage',            new Color(40, 167, 69)]
    ]
    metrics.each { m -> metTable.addCell(metricCell(m[0], m[1], m[2])) }
    doc.add(metTable)
    
    def metTable2 = new PdfPTable(3)
    metTable2.widthPercentage = 100
    def metrics2 = [
        [safe(env.SONAR_HOTSPOTS, '0'),          'Security Hotspots', new Color(108, 117, 125)],
        ["${safe(env.SONAR_DUPLICATION, '0.0')}%", 'Duplication',     new Color(108, 117, 125)],
        [safe(env.SONAR_LINES, '0'),             'Lines of Code',     new Color(108, 117, 125)]
    ]
    metrics2.each { m -> metTable2.addCell(metricCell(m[0], m[1], m[2])) }
    doc.add(metTable2)
    doc.add(Chunk.NEWLINE)
    
    // ─── SEVERITY BREAKDOWN ───
    addSection("Issue Severity Breakdown")
    def sevTable = new PdfPTable(5)
    sevTable.widthPercentage = 100
    def severities = [
        [safe(env.SONAR_BLOCKER, '0'),  'BLOCKER',  fontRed],
        [safe(env.SONAR_CRITICAL, '0'), 'CRITICAL', fontRed],
        [safe(env.SONAR_MAJOR, '0'),    'MAJOR',    fontOrange],
        [safe(env.SONAR_MINOR, '0'),    'MINOR',    fontGray],
        [safe(env.SONAR_INFO, '0'),     'INFO',     fontCyan]
    ]
    severities.each { s ->
        def inner = new PdfPTable(1)
        inner.widthPercentage = 100
        def c1 = new PdfPCell(new Phrase(s[0], s[2]))
        c1.horizontalAlignment = Element.ALIGN_CENTER
        c1.border = Rectangle.NO_BORDER; c1.paddingTop = 8; c1.paddingBottom = 4
        def c2 = new PdfPCell(new Phrase(s[1], new Font(Font.HELVETICA, 8, Font.BOLD, new Color(108, 117, 125))))
        c2.horizontalAlignment = Element.ALIGN_CENTER
        c2.border = Rectangle.NO_BORDER; c2.paddingTop = 2; c2.paddingBottom = 8
        inner.addCell(c1); inner.addCell(c2)
        def wrap = new PdfPCell(inner)
        wrap.backgroundColor = new Color(248, 249, 250)
        wrap.border = Rectangle.NO_BORDER
        wrap.padding = 4
        sevTable.addCell(wrap)
    }
    doc.add(sevTable)
    doc.add(Chunk.NEWLINE)
    
    // ─── QUICK LINKS ───
    addSection("Quick Links")
    def links = [
        ["Dashboard",    "${sonarHost}/dashboard?id=${projectKey}"],
        ["Metrics",      "${sonarHost}/component_measures?id=${projectKey}"],
        ["All Issues",   "${sonarHost}/project/issues?id=${projectKey}&resolved=false"],
        ["New Issues",   "${sonarHost}/project/issues?id=${projectKey}&resolved=false&sinceLeakPeriod=true"],
        ["Hotspots",     "${sonarHost}/security_hotspots?id=${projectKey}"],
        ["Jenkins Console", "${safe(env.BUILD_URL)}console"]
    ]
    links.each { link ->
        def p = new Paragraph()
        p.add(new Chunk("${link[0]}: ", fontLabel))
        def anchor = new Anchor(link[1], fontLink)
        anchor.reference = link[1]
        p.add(anchor)
        p.spacingAfter = 4
        doc.add(p)
    }
    
    // ─── FOOTER ───
    doc.add(Chunk.NEWLINE)
    addHorizontalRule(new Color(233, 236, 239))
    def footerP = new Paragraph("Generated by Jenkins | Build #${safe(env.BUILD_NUMBER)} | ${new Date().format('yyyy-MM-dd HH:mm:ss')}", fontFooter)
    footerP.alignment = Element.ALIGN_CENTER
    footerP.spacingBefore = 8
    doc.add(footerP)
    
    doc.close()
    out.close()
    
    echo "Native PDF generated: ${pdfName}"
    
    // ─── EMAIL WITH PDF ATTACHMENT ───
    emailext (
        subject: "[${statusText}] SonarQube Report: ${safe(env.JOB_NAME)} #${safe(env.BUILD_NUMBER)}",
        body: """
            <p>Please find the attached SonarQube Quality Report (PDF).</p>
            <p><strong>Project:</strong> ${safe(env.JOB_NAME)}<br>
               <strong>Build:</strong> #${safe(env.BUILD_NUMBER)}<br>
               <strong>Status:</strong> ${statusText}<br>
               <strong>Quality Gate:</strong> ${safe(env.SONAR_STATUS, 'UNKNOWN')}</p>
            <p><em>Automated report from Jenkins.</em></p>
        """,
        to: recipient,
        from: fromEmail,
        mimeType: 'text/html',
        attachmentsPattern: pdfName,
        attachLog: true
    )
    
    // Cleanup
    sh "rm -f ${pdfName}"
    echo "Cleaned up PDF file."
}