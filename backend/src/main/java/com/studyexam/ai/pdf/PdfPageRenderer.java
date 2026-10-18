package com.studyexam.ai.pdf;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 把 PDF 逐页渲染成图片,交给视觉模型识别。
 *
 * <p>为什么走渲染而不是抽文本:真题 PDF 很多是**扫描件**(没有文字层),
 * 本项目实测的一份英语试题 pdftotext 抽 3 页只得到 3 个字节——那种文件只能靠视觉。
 * 而文字层 PDF 用视觉识别同样可行,代价是每页多花一点 token,换来的是对排版、
 * 表格、下划线、手写标记的完整理解,对试卷这种强排版文档反而更稳。
 *
 * <p>输出 JPEG(质量 0.85)而不是 PNG:一页 A4 在 150 DPI 下约 200—400KB,
 * 发往模型时不至于把请求体撑得太大。
 */
@Component
public class PdfPageRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfPageRenderer.class);
    private static final float JPEG_QUALITY = 0.85f;

    /** 渲染结果:页码(从 1 开始)+ JPEG 字节。 */
    public record RenderedPage(int pageNumber, byte[] jpeg) {
    }

    public record Rendered(List<RenderedPage> pages, int totalPages) {
    }

    public Rendered render(byte[] pdfBytes, int dpi, int maxPages) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int total = document.getNumberOfPages();
            if (total == 0) {
                throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR, "这份 PDF 没有任何页面");
            }
            int limit = Math.min(total, maxPages);

            PDFRenderer renderer = new PDFRenderer(document);
            List<RenderedPage> pages = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                pages.add(new RenderedPage(i + 1, toJpeg(image)));
            }
            log.info("PDF 渲染完成:共 {} 页,本次处理 {} 页,{} DPI", total, pages.size(), dpi);
            return new Rendered(pages, total);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR,
                    "这份 PDF 打不开,可能已损坏或加了密码:" + e.getMessage());
        }
    }

    private byte[] toJpeg(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                // 理论上不会发生;退化成默认编码,保证功能可用
                ImageIO.write(image, "jpeg", out);
                return out.toByteArray();
            }
            ImageWriter writer = writers.next();
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(stream);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR,
                    "页面转图片失败:" + e.getMessage());
        }
    }

    /** 给单测用:判断一段字节是不是 PDF。 */
    public static boolean looksLikePdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            return false;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            byte[] head = new byte[5];
            if (in.read(head) != 5) {
                return false;
            }
            return "%PDF-".equals(new String(head, java.nio.charset.StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }
}