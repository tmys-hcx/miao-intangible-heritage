package com.mic.culture.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intangible_culture")
public class IntangibleCulture {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "发布人ID不能为空")
    private Long publisherId;

    @NotBlank(message = "非遗文化标题不能为空")
    @Size(min = 2, max = 120, message = "标题长度2~120字符")
    private String title;

    @NotBlank(message = "非遗文化内容不能为空")
    private String content;

    /** 图片链接（多张图为JSON数组字符串）：可为空，与视频链接至少其一（入参校验见 CultureSaveDTO） */
    @Size(max = 255, message = "图片链接不能超过255字符")
    private String imgUrl;

    @Size(max = 255, message = "视频链接不能超过255字符")
    private String videoUrl;

    /** 视频封面缩略图OSS地址：无图片的纯视频文章由发布端自动截帧上传，供列表卡片缩略图与详情页视频海报展示 */
    @Size(max = 1024, message = "视频封面地址不能超过1024字符")
    private String coverUrl;

    @NotNull(message = "内容状态不能为空")
    private Integer status;

    @NotNull(message = "浏览量不能为空")
    @PositiveOrZero(message = "浏览量不能为负数")
    private Long viewCount;

    /** 排版顺序：数值越大越靠前（0=未排版，按发布时间倒序排在已排版文章之后）；
     * 由管理员在「非遗文化排版」页拖拽保存（saveSortOrder），前台默认排序（sortBy=custom）按此字段倒序 */
    private Long sortOrder;

    /** 审核备注（非表字段）：审核驳回时为不合格原因，出参时从 Redis 审核记录回填 */
    @TableField(exist = false)
    private String auditRemark;

    /** 审核时间（非表字段）：yyyy-MM-dd HH:mm:ss，出参时从 Redis 审核记录回填 */
    @TableField(exist = false)
    private String auditTime;

    /** 发布人账号（非表字段）：管理端列表自定义 SQL 关联 sys_user 回填，展示发布人替代ID */
    @TableField(exist = false)
    private String publisherAccount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
