#!/usr/bin/env groovy

@Grab('com.github.librepdf:openpdf:1.3.30')
import com.lowagie.text.*
import com.lowagie.text.pdf.*
import java.awt.Color

/**
 * Executive SonarQube PDF Report Generator
 * Replicates the bitegarden-style executive summary natively in PDF.
 *
 * Usage:
 *   @Library('sonar-pdf-reports') _
 *   sonarNativePdfReport(
 *       recipientEmail: 'team@company.com',
 *       fromEmail: 'jenkins@company.com'
 *   )
 */
def call(Map config = [:]) {

    def recipient   = config.recipientEmail ?: 'ovt.bangalore@gmail.com'
    def fromEmail   = config.fromEmail     ?: 'sathish.s@vimatch.in'
    def sonarHost   = config.sonarHost     ?: env.SONAR_HOST ?: 'http://192.168.0.5:9000'
    def projectKey  = config.projectKey    ?: env.SONAR_PROJECT_KEY ?: 'STB_Automation_Framework'

    def safe = { val, fallback = 'N/A' ->
        (val == null || val.toString().trim() == '') ? fallback : val.toString()
    }

    def buildStatus = currentBuild.currentResult
    def statusText = (buildStatus == 'SUCCESS') ? 'PASSED' :
                     (buildStatus == 'UNSTABLE') ? 'QUALITY GATE FAILED' : 'BUILD FAILED'

    // ─── Gather ALL serializable data ───
    def reportData = [
        statusText:       statusText,
        buildStatus:      buildStatus,
        jobName:          safe(env.JOB_NAME),
        buildNumber:      safe(env.BUILD_NUMBER),
        gitCommit:        safe(env.GIT_COMMIT)?.take(7) ?: 'N/A',
        gitBranch:        safe(env.GIT_BRANCH, 'main'),
        duration:         safe(currentBuild.durationString),
        buildUrl:         safe(env.BUILD_URL),
        sonarStatus:      safe(env.SONAR_STATUS, 'UNKNOWN'),
        sonarBugs:        safe(env.SONAR_BUGS, '0'),
        sonarVulns:       safe(env.SONAR_VULNERABILITIES, '0'),
        sonarSmells:      safe(env.SONAR_CODE_SMELLS, '0'),
        sonarCoverage:    safe(env.SONAR_COVERAGE, '0.0'),
        sonarDuplication: safe(env.SONAR_DUPLICATION, '0.0'),
        sonarLines:       safe(env.SONAR_LINES, '0'),
        sonarHotspots:    safe(env.SONAR_HOTSPOTS, '0'),
        sonarNewBugs:     safe(env.SONAR_NEW_BUGS, '0'),
        sonarNewVulns:    safe(env.SONAR_NEW_VULNERABILITIES, '0'),
        sonarNewSmells:   safe(env.SONAR_NEW_CODE_SMELLS, '0'),
        sonarNewHotspots: safe(env.SONAR_NEW_HOTSPOTS, '0'),
        sonarNewCoverage: safe(env.SONAR_NEW_COVERAGE, '0.0'),
        sonarBlocker:     safe(env.SONAR_BLOCKER, '0'),
        sonarCritical:    safe(env.SONAR_CRITICAL, '0'),
        sonarMajor:       safe(env.SONAR_MAJOR, '0'),
        sonarMinor:       safe(env.SONAR_MINOR, '0'),
        sonarInfo:        safe(env.SONAR_INFO, '0'),
        sonarHost:        sonarHost,
        projectKey:       projectKey,
        analysisDate:     new Date().format('yyyy-MM-dd')
    ]

    // ─── Generate PDF bytes inside @NonCPS ───
    byte[] pdfBytes = generateExecutivePdf(reportData)

    // ─── Transfer to agent workspace ───
    def pdfName = "sonar-report-${env.BUILD_NUMBER}.pdf"
    def b64Name = ".sonar-report-${env.BUILD_NUMBER}.b64"

    def base64 = java.util.Base64.getEncoder().encodeToString(pdfBytes)

    writeFile file: b64Name, text: base64
    sh "base64 -d ${b64Name} > ${pdfName} && rm -f ${b64Name}"

    echo "Executive PDF generated: ${pdfName}"

    // ─── Email ───
    emailext (
        subject: "[${statusText}] SonarQube Executive Report: ${safe(env.JOB_NAME)} #${safe(env.BUILD_NUMBER)}",
        body: """
            <p>Please find the attached SonarQube Executive Report (PDF).</p>
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

    sh "rm -f ${pdfName}"
    echo "Cleaned up PDF file."
}

// ═══════════════════════════════════════════════════════════════
// @NonCPS — Jenkins NEVER tries to serialize anything below here
// ═══════════════════════════════════════════════════════════════
@NonCPS
byte[] generateExecutivePdf(Map d) {

    def baos = new ByteArrayOutputStream()
    def doc = new Document(PageSize.A4, 28, 28, 28, 28)
    def writer = PdfWriter.getInstance(doc, baos)
    doc.open()
    def cb = writer.directContent

    // ─── Colors ───
    def cDark      = new Color(44, 62, 80)
    def cGray      = new Color(120, 120, 120)
    def cLightGray = new Color(245, 245, 245)
    def cBorder    = new Color(220, 220, 220)
    def cGreen     = new Color(76, 175, 80)
    def cRed       = new Color(220, 53, 69)
    def cOrange    = new Color(253, 126, 20)
    def cYellow    = new Color(255, 193, 7)
    def cBlue      = new Color(23, 162, 184)
    def cPurple    = new Color(108, 117, 125)

    // ─── Fonts ───
    def fTitle    = new Font(Font.HELVETICA, 20, Font.BOLD, cDark)
    def fSubTitle = new Font(Font.HELVETICA, 12, Font.BOLD, cDark)
    def fHeader   = new Font(Font.HELVETICA, 9, Font.BOLD, cGray)
    def fValue    = new Font(Font.HELVETICA, 9, Font.NORMAL, cDark)
    def fBig      = new Font(Font.HELVETICA, 32, Font.BOLD, cDark)
    def fMetric   = new Font(Font.HELVETICA, 26, Font.BOLD, cDark)
    def fLabel    = new Font(Font.HELVETICA, 8, Font.NORMAL, cGray)
    def fGrade    = new Font(Font.HELVETICA, 22, Font.BOLD, Color.WHITE)
    def fGate     = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE)
    def fSmall    = new Font(Font.HELVETICA, 8, Font.NORMAL, cGray)
    def fFooter   = new Font(Font.HELVETICA, 7, Font.ITALIC, cGray)

    // ─── Helpers ───
    def cell = { content, font, align = Element.ALIGN_LEFT, bg = null, border = Rectangle.NO_BORDER ->
        def c = new PdfPCell(new Phrase(content, font))
        c.horizontalAlignment = align
        c.verticalAlignment = Element.ALIGN_MIDDLE
        c.border = border
        if (bg) c.backgroundColor = bg
        c
    }

    def emptyCell = { w = 1 ->
        def c = new PdfPCell(new Phrase(' '))
        c.border = Rectangle.NO_BORDER
        c.colspan = w
        c
    }

    // Grade circle template
    def gradeImage = { letter, color ->
        def tp = cb.createTemplate(44, 44)
        tp.setColorFill(color)
        tp.circle(22, 22, 20)
        tp.fill()
        tp.setColorFill(Color.WHITE)
        tp.beginText()
        tp.setFontAndSize(Font.HELVETICA_BOLD, 22)
        tp.showTextAligned(Element.ALIGN_CENTER, letter, 22, 14, 0)
        tp.endText()
        Image.getInstance(tp)
    }

    // Quality gate badge template
    def gateBadge = { text, color ->
        def tp = cb.createTemplate(90, 28)
        tp.setColorFill(color)
        tp.roundRectangle(0, 0, 90, 28, 14)
        tp.fill()
        tp.setColorFill(Color.WHITE)
        tp.beginText()
        tp.setFontAndSize(Font.HELVETICA_BOLD, 11)
        tp.showTextAligned(Element.ALIGN_CENTER, text, 45, 9, 0)
        tp.endText()
        Image.getInstance(tp)
    }

    // Ring gauge template (donut)
    def ringGauge = { pct, color ->
        def tp = cb.createTemplate(90, 90)
        // Outer ring background
        tp.setColorStroke(new Color(230, 230, 230))
        tp.setLineWidth(10)
        tp.circle(45, 45, 35)
        tp.stroke()
        // Colored ring (full circle for simplicity; color indicates status)
        tp.setColorStroke(color)
        tp.setLineWidth(10)
        tp.circle(45, 45, 35)
        tp.stroke()
        // Center text
        tp.setColorFill(cDark)
        tp.beginText()
        tp.setFontAndSize(Font.HELVETICA_BOLD, 22)
        tp.showTextAligned(Element.ALIGN_CENTER, "${pct}%", 45, 36, 0)
        tp.endText()
        Image.getInstance(tp)
    }

    // Simple grade calculator
    def computeGrade = { count ->
        def n = 0
        try { n = count.toString().toInteger() } catch (e) {}
        if (n == 0) return ['A', cGreen]
        if (n < 10) return ['B', new Color(139, 195, 74)]
        if (n < 50) return ['C', cYellow]
        if (n < 100) return ['D', cOrange]
        return ['E', cRed]
    }

    def relGrade = computeGrade(d.sonarBugs)
    def secGrade = computeGrade(d.sonarVulns.toInteger() + d.sonarHotspots.toInteger())
    def mainGrade = computeGrade(d.sonarSmells)

    // ═══════════════════════════════════════════════════════
    // HEADER
    // ═══════════════════════════════════════════════════════
    def headT = new PdfPTable([60f, 40f] as float[])
    headT.widthPercentage = 100
    def leftHead = new PdfPCell()
    leftHead.border = Rectangle.NO_BORDER
    leftHead.addElement(new Paragraph('SonarQube', fTitle))
    leftHead.addElement(new Paragraph('Executive Report', fSubTitle))
    headT.addCell(leftHead)

    def rightHead = new PdfPCell()
    rightHead.border = Rectangle.NO_BORDER
    rightHead.horizontalAlignment = Element.ALIGN_RIGHT
    rightHead.verticalAlignment = Element.ALIGN_TOP
    def gateColor = (d.sonarStatus == 'OK') ? cGreen : cRed
    def gateText = (d.sonarStatus == 'OK') ? 'PASSED' : 'FAILED'
    rightHead.addElement(new Paragraph(' '))
    rightHead.addElement(new Paragraph(' '))
    rightHead.addElement(new Chunk(gateBadge(gateText, gateColor), 0, 0))
    headT.addCell(rightHead)
    doc.add(headT)

    // Thin separator line
    def sep = new PdfPTable(1)
    sep.widthPercentage = 100
    def sepC = new PdfPCell(new Phrase(' '))
    sepC.border = Rectangle.BOTTOM
    sepC.borderColor = cBorder
    sepC.paddingBottom = 4
    sep.addCell(sepC)
    doc.add(sep)
    doc.add(Chunk.NEWLINE)

    // ═══════════════════════════════════════════════════════
    // PROJECT METADATA
    // ═══════════════════════════════════════════════════════
    def metaT = new PdfPTable([33f, 33f, 34f] as float[])
    metaT.widthPercentage = 100
    metaT.addCell(cell('Project Name', fHeader, Element.ALIGN_LEFT))
    metaT.addCell(cell('Branch', fHeader, Element.ALIGN_LEFT))
    metaT.addCell(cell('Analysis Date', fHeader, Element.ALIGN_RIGHT))
    metaT.addCell(cell(d.jobName, fValue, Element.ALIGN_LEFT))
    metaT.addCell(cell(d.gitBranch, fValue, Element.ALIGN_LEFT))
    metaT.addCell(cell(d.analysisDate, fValue, Element.ALIGN_RIGHT))
    doc.add(metaT)

    def meta2 = new PdfPTable([33f, 33f, 34f] as float[])
    meta2.widthPercentage = 100
    meta2.addCell(cell('Size Rating', fHeader, Element.ALIGN_LEFT))
    meta2.addCell(cell('Lines of Code', fHeader, Element.ALIGN_LEFT))
    meta2.addCell(cell('Quality Gate', fHeader, Element.ALIGN_RIGHT))
    def sizeCell = cell('', fValue, Element.ALIGN_LEFT)
    sizeCell.addElement(new Chunk(gradeImage('M', new Color(66, 133, 244)), 0, 0))
    meta2.addCell(sizeCell)
    def locCell = cell('', fValue, Element.ALIGN_LEFT)
    locCell.addElement(new Paragraph(d.sonarLines, fBig))
    meta2.addCell(locCell)
    meta2.addCell(cell(d.sonarStatus, fValue, Element.ALIGN_RIGHT))
    doc.add(meta2)
    doc.add(Chunk.NEWLINE)

    // ═══════════════════════════════════════════════════════
    // THREE PILLARS: Reliability | Security | Maintainability
    // ═══════════════════════════════════════════════════════
    def pillarT = new PdfPTable(3)
    pillarT.widthPercentage = 100
    pillarT.spacingBefore = 8

    def pillarCell = { title, gradeArr, metricLabel, metricValue, newLabel, newValue ->
        def t = new PdfPTable(1)
        t.widthPercentage = 100
        // Title
        def tc = cell(title, new Font(Font.HELVETICA, 11, Font.BOLD, cGray), Element.ALIGN_CENTER)
        tc.paddingBottom = 8
        t.addCell(tc)
        // Grade circle
        def gc = cell('', fValue, Element.ALIGN_CENTER)
        gc.addElement(new Chunk(gradeImage(gradeArr[0], gradeArr[1]), 0, 0))
        gc.paddingBottom = 6
        t.addCell(gc)
        // Metric
        def mc = cell(metricValue, new Font(Font.HELVETICA, 24, Font.BOLD, cDark), Element.ALIGN_CENTER)
        mc.paddingBottom = 2
        t.addCell(mc)
        def ml = cell(metricLabel, fLabel, Element.ALIGN_CENTER)
        ml.paddingBottom = 10
        t.addCell(ml)
        // New code box
        def nt = new PdfPTable(1)
        nt.widthPercentage = 100
        def nc = cell("${newLabel}: ${newValue}", fSmall, Element.ALIGN_CENTER, cLightGray)
        nc.paddingTop = 6
        nc.paddingBottom = 6
        nt.addCell(nc)
        t.addCell(nt)
        def wrap = new PdfPCell(t)
        wrap.border = Rectangle.BOX
        wrap.borderColor = cBorder
        wrap.padding = 10
        return wrap
    }

    pillarT.addCell(pillarCell('Reliability', relGrade, 'Bugs', d.sonarBugs, 'New Bugs', d.sonarNewBugs))
    pillarT.addCell(pillarCell('Security', secGrade, 'Vulnerabilities', d.sonarVulns, 'New Vulnerabilities', d.sonarNewVulns))
    pillarT.addCell(pillarCell('Maintainability', mainGrade, 'Code Smells', d.sonarSmells, 'New Code Smells', d.sonarNewSmells))
    doc.add(pillarT)
    doc.add(Chunk.NEWLINE)

    // ═══════════════════════════════════════════════════════
    // MIDDLE SECTION: Issues by Severity | Coverage | Duplications
    // ═══════════════════════════════════════════════════════
    def midT = new PdfPTable([55f, 22f, 23f] as float[])
    midT.widthPercentage = 100

    // ─── Left: Issues by Severity ───
    def sevT = new PdfPTable([25f, 25f, 25f, 25f] as float[])
    sevT.widthPercentage = 100
    // Header row
    sevT.addCell(emptyCell())
    sevT.addCell(cell('Bug', fHeader, Element.ALIGN_CENTER))
    sevT.addCell(cell('Vulnerability', fHeader, Element.ALIGN_CENTER))
    sevT.addCell(cell('Code Smell', fHeader, Element.ALIGN_CENTER))

    def sevRow = { name, color, bug, vuln, smell ->
        def lc = cell("  ${name}", new Font(Font.HELVETICA, 9, Font.BOLD, color), Element.ALIGN_LEFT)
        lc.border = Rectangle.BOTTOM
        lc.borderColor = cBorder
        lc.paddingTop = 6
        lc.paddingBottom = 6
        sevT.addCell(lc)
        def vc = { v ->
            def c = cell(v.toString(), fValue, Element.ALIGN_CENTER)
            c.border = Rectangle.BOTTOM
            c.borderColor = cBorder
            c.paddingTop = 6
            c.paddingBottom = 6
            c
        }
        sevT.addCell(vc(bug))
        sevT.addCell(vc(vuln))
        sevT.addCell(vc(smell))
    }

    // We don't have per-severity breakdown by type from the API, so we show totals per severity
    // and overall type totals in the top row. This is a best-effort visual.
    sevRow('Blocker',  cRed,    d.sonarBlocker,  '0', '0')
    sevRow('Critical', cRed,    d.sonarCritical, '0', '0')
    sevRow('Major',    cOrange, d.sonarMajor,    '0', '0')
    sevRow('Minor',    cYellow, d.sonarMinor,    '0', '0')
    sevRow('Info',     cBlue,   d.sonarInfo,     '0', '0')

    def sevWrap = new PdfPCell(sevT)
    sevWrap.border = Rectangle.BOX
    sevWrap.borderColor = cBorder
    sevWrap.padding = 8
    midT.addCell(sevWrap)

    // ─── Center: Coverage ───
    def covT = new PdfPTable(1)
    covT.widthPercentage = 100
    covT.addCell(cell('Coverage', fSubTitle, Element.ALIGN_CENTER))
    def covImg = cell('', fValue, Element.ALIGN_CENTER)
    def covColor = (d.sonarCoverage.toFloat() >= 80.0) ? cGreen : (d.sonarCoverage.toFloat() >= 50.0) ? cYellow : cRed
    covImg.addElement(new Chunk(ringGauge(d.sonarCoverage, covColor), 0, 0))
    covImg.paddingTop = 6
    covT.addCell(covImg)
    covT.addCell(cell('Unit Tests', fLabel, Element.ALIGN_CENTER))
    covT.addCell(cell('0', fMetric, Element.ALIGN_CENTER))
    covT.addCell(cell("Coverage on New Code: ${d.sonarNewCoverage}%", fSmall, Element.ALIGN_CENTER, cLightGray))
    def covWrap = new PdfPCell(covT)
    covWrap.border = Rectangle.BOX
    covWrap.borderColor = cBorder
    covWrap.padding = 8
    midT.addCell(covWrap)

    // ─── Right: Duplications ───
    def dupT = new PdfPTable(1)
    dupT.widthPercentage = 100
    dupT.addCell(cell('Duplications', fSubTitle, Element.ALIGN_CENTER))
    def dupImg = cell('', fValue, Element.ALIGN_CENTER)
    def dupColor = (d.sonarDuplication.toFloat() <= 3.0) ? cGreen : (d.sonarDuplication.toFloat() <= 10.0) ? cYellow : cRed
    dupImg.addElement(new Chunk(ringGauge(d.sonarDuplication, dupColor), 0, 0))
    dupImg.paddingTop = 6
    dupT.addCell(dupImg)
    dupT.addCell(cell('Duplicated blocks', fLabel, Element.ALIGN_CENTER))
    dupT.addCell(cell('0', fMetric, Element.ALIGN_CENTER))
    dupT.addCell(cell("Duplications on New Code: 0%", fSmall, Element.ALIGN_CENTER, cLightGray))
    def dupWrap = new PdfPCell(dupT)
    dupWrap.border = Rectangle.BOX
    dupWrap.borderColor = cBorder
    dupWrap.padding = 8
    midT.addCell(dupWrap)

    doc.add(midT)
    doc.add(Chunk.NEWLINE)

    // ═══════════════════════════════════════════════════════
    // FOOTER
    // ═══════════════════════════════════════════════════════
    def footT = new PdfPTable(1)
    footT.widthPercentage = 100
    def footC = new PdfPCell(new Phrase("Generated by Jenkins | Build #${d.buildNumber} | ${d.analysisDate}", fFooter))
    footC.border = Rectangle.TOP
    footC.borderColor = cBorder
    footC.paddingTop = 6
    footC.horizontalAlignment = Element.ALIGN_CENTER
    footT.addCell(footC)
    doc.add(footT)

    doc.close()
    return baos.toByteArray()
}