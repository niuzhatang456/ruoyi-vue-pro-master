$ErrorActionPreference = 'Stop'

$pptPath = 'C:\Users\牛轧糖\Desktop\小楼本科毕设\完稿文件\上海音乐学院.pptx'
$backupPath = 'C:\Users\牛轧糖\Desktop\小楼本科毕设\完稿文件\上海音乐学院.pptx.bak'

if (-not (Test-Path -LiteralPath $pptPath)) {
  throw 'PPT not found: $pptPath'
}
if (-not (Test-Path -LiteralPath $backupPath)) {
  Copy-Item -LiteralPath $pptPath -Destination $backupPath
}

$outline = @(
  @{ title = '视错觉在舞台多媒体设计中的运用'; body = @('以多媒体舞台作品《浮生之境》为例', '姓名：', '专业：', '指导老师：', '学校：', '答辩时间：') },
  @{ title = '汇报目录'; body = @('研究背景与意义', '视错觉原理概述', '舞台多媒体设计特征', '视错觉在舞台中的应用方式', '《浮生之境》实践分析', '研究结论与不足') },
  @{ title = '研究背景'; body = @('数字影像、LED屏幕、投影等技术推动舞台形式变化', '舞台由实体布景转向虚实结合的综合空间', '观众对沉浸式、动态化、视觉冲击力的要求提高', '视错觉成为舞台空间塑造的重要方法', '讲述重点：现代舞台不只是“搭景”，而是通过影像、灯光和空间共同创造视觉体验。') },
  @{ title = '研究意义'; body = @('从视觉感知角度理解舞台多媒体设计', '探索视错觉在空间重构中的作用', '分析视错觉对舞台叙事和沉浸体验的提升', '为舞台视觉设计提供新的创作思路') },
  @{ title = '研究方法'; body = @('文献研究法：梳理视错觉理论、舞台多媒体相关研究', '案例分析法：分析经典舞台作品中的视错觉表现方式', '实践研究法：结合原创作品《浮生之境》进行设计验证') },
  @{ title = '什么是视错觉？'; body = @('视觉系统受到生理、心理和环境因素影响', '人眼看到的内容与客观事实产生差异', '本质是视觉感知和大脑认知之间的偏差', '在舞台中可转化为空间、运动和形态的艺术表达') },
  @{ title = '视错觉的形成机制'; body = @('视觉暂留：连续画面形成动态感', '空间感知偏差：平面影像形成纵深感', '经验判断影响：观众根据已有经验理解空间', '图形结构冲突：线条、比例、方向造成误判') },
  @{ title = '三类主要视错觉'; body = @('空间视错觉：拓展舞台纵深与层次', '运动视错觉：制造流动、旋转、漂浮感', '几何视错觉：增强图形张力与结构变化') },
  @{ title = '空间视错觉：重构舞台空间'; body = @('利用透视、投影、光影制造空间纵深', '模糊真实空间与虚拟空间边界', '在有限舞台中创造更大的空间感', '常用于建筑、通道、深景、悬浮空间表现') },
  @{ title = '运动视错觉：制造动态感'; body = @('利用连续图像、灯光变化和节奏切换', '让静态舞台产生运动效果', '可表现流动、旋转、坠落、漂浮等视觉状态', '常与音乐节奏和演员动作配合') },
  @{ title = '几何视错觉：强化视觉张力'; body = @('通过线条、比例、方向和角度造成视觉误判', '打破观众对稳定空间的认知', '增强舞台画面的结构感和冲击力', '适合表现错位、压迫、扭曲等情绪') },
  @{ title = '舞台多媒体的四个特征'; body = @('动态性：影像和灯光随时间变化', '叙事性：影像参与剧情推进', '虚实性：真实布景与虚拟影像融合', '沉浸性：观众进入被影像包围的空间') },
  @{ title = '动态性：让舞台“动起来”'; body = @('影像变化带来空间扩展与收缩', '灯光变化强化节奏与情绪', '动态视觉引导观众注意力', '形成有生命感的舞台空间') },
  @{ title = '叙事性：影像参与讲故事'; body = @('多媒体影像可以完成场景转换', '可表现时间流动、心理变化和情绪氛围', '减少传统换景时间', '增强舞台叙事的连贯性') },
  @{ title = '虚实性：真实与影像的融合'; body = @('实体布景与虚拟影像共同构成空间', '观众难以区分真实结构和虚拟画面', '虚实叠加增强空间层次', '为视错觉产生提供基础') },
  @{ title = '沉浸性：从观看到进入'; body = @('大面积投影和灯光形成包围感', '影像延伸至墙面、地面或观众区域', '观众从“看舞台”变成“进入舞台”', '增强身体在场感和情绪代入') },
  @{ title = '视错觉在舞台中的应用路径'; body = @('感官认知层面：引导观众视觉注意', '艺术表达层面：强化空间、情绪和叙事', '技术实现层面：通过投影、LED、灯光、纱幕实现') },
  @{ title = '实践作品：《浮生之境》'; body = @('主题：无重力空间', '核心概念：打破传统三维空间限制', '表达重点：人与空间的相互塑造关系', '视觉特征：漂浮、旋转、错位、折叠、失重') },
  @{ title = '从现实空间到无重力空间'; body = @('重心不再垂直于地面', '舞步摆脱前后左右的常规动线', '舞台从二维平面转化为多维空间', '观众重新感知身体与空间的关系') },
  @{ title = '为什么使用视错觉？'; body = @('视错觉能够打破常规空间逻辑', '倒置、错位、漂浮等手法契合“失重”主题', '动态错觉表现空间的不稳定状态', '虚实结合强化超现实舞台体验') },
  @{ title = '空间重构：倒置与漂浮'; body = @('倒置房间制造方向感错乱', '漂浮建筑打破现实重力逻辑', '错位墙体形成空间穿插效果', '多层投影增强纵深与立体感') },
  @{ title = '动态变化：旋转与失控'; body = @('通过影像旋转表现无重力环境', '画面运动与演员动作形成呼应', '高潮段落加大空间旋转角度', '视觉节奏推动情绪变化') },
  @{ title = '章节节奏与视觉变化'; body = @('第一幕：博物馆场景，现实铺垫', '第二幕：进入无重力环境', '第三、四幕：空间失控，视觉高潮', '第五幕：逐渐适应，运动变缓', '第六幕：回到现实，空间恢复稳定') },
  @{ title = '技术实现方式'; body = @('使用 UE 构建重力失效的空间效果', '参考 Cinta Vidal 的非重力建筑结构', '通过天幕与纱幕分层制造立体感', '演员动作与投影影像衔接', '打破实体舞台与虚拟影像边界') },
  @{ title = '《浮生之境》的实践效果'; body = @('强化舞台空间层次', '提升观众对场景变化的感知', '增强失重与漂浮的视觉体验', '推动作品叙事和情绪表达', '验证视错觉在舞台多媒体中的可行性') },
  @{ title = '研究结论'; body = @('视错觉可以突破舞台物理空间限制', '视错觉能够增强舞台叙事表达', '视错觉提升观众沉浸体验', '多媒体技术为视错觉应用提供实现条件', '视错觉可作为舞台视觉设计的重要方法') },
  @{ title = '不足与未来展望'; body = @('不足：研究时间有限；对交互式舞台研究不够深入；对实时影像技术探讨不足', '展望：未来可结合实时互动、VR、AR等技术', '拓展视错觉在沉浸式演出中的应用', '推动舞台视觉设计向跨媒介方向发展') },
  @{ title = '感谢聆听'; body = @('恳请各位老师批评指正。') }
)

$nav = @('研究背景与意义', '视错觉原理概述', '舞台多媒体设计特征', '视错觉应用方式', '《浮生之境》实践分析', '研究结论与不足')

function Get-TextShapes {
  param($Shapes)
  $items = New-Object System.Collections.Generic.List[object]
  foreach ($shape in $Shapes) {
    try {
      if ($shape.Type -eq 6) {
        $childItems = Get-TextShapes $shape.GroupItems
        foreach ($child in $childItems) { $items.Add($child) }
      } elseif ($shape.HasTextFrame -and $shape.TextFrame.HasText) {
        $text = $shape.TextFrame.TextRange.Text
        if ($null -ne $text -and $text.Trim().Length -gt 0) {
          $items.Add([pscustomobject]@{
            Shape = $shape
            Text = $text.Trim()
            Left = [double]$shape.Left
            Top = [double]$shape.Top
            Width = [double]$shape.Width
            Height = [double]$shape.Height
            Area = [double]($shape.Width * $shape.Height)
          })
        }
      }
    } catch {
      # Some grouped or placeholder shapes can throw; skip them without changing design.
    }
  }
  return $items
}

function Set-ShapeText {
  param($Shape, [string[]]$Lines)
  $Shape.TextFrame.TextRange.Text = ($Lines -join [Environment]::NewLine)
}

function Clear-ShapeText {
  param($Shape)
  $Shape.TextFrame.TextRange.Text = ''
}

$ppt = $null
$presentation = $null
try {
  $ppt = New-Object -ComObject PowerPoint.Application
  $ppt.Visible = [Microsoft.Office.Core.MsoTriState]::msoTrue
  $presentation = $ppt.Presentations.Open($pptPath, $false, $false, $false)

  while ($presentation.Slides.Count -gt 28) {
    $presentation.Slides.Item($presentation.Slides.Count).Delete()
  }

  for ($i = 1; $i -le 28; $i++) {
    $slide = $presentation.Slides.Item($i)
    $entry = $outline[$i - 1]
    $items = @(Get-TextShapes $slide.Shapes)
    $assigned = New-Object System.Collections.Generic.HashSet[int]

    function Mark($item, [string[]]$lines) {
      Set-ShapeText $item.Shape $lines
      [void]$assigned.Add([int]$item.Shape.Id)
    }

    if ($i -eq 1) {
      $main = $items | Sort-Object Area -Descending | Select-Object -First 1
      if ($main) { Mark $main @($entry.title, $entry.body[0]) }
      $bottom = @($items | Where-Object { $_.Top -gt 330 -and $_.Shape.Id -ne $main.Shape.Id } | Sort-Object Left)
      if ($bottom.Count -ge 3) {
        Mark $bottom[0] @('姓名：', '专业：')
        Mark $bottom[1] @('指导老师：', '学校：')
        Mark $bottom[2] @('答辩时间：')
      }
    } elseif ($i -eq 2) {
      $titleShape = $items | Where-Object { $_.Text -eq '目录' } | Select-Object -First 1
      if ($titleShape) { Mark $titleShape @($entry.title) }
      $oldMenu = @('选题背景与意义', '研究内容与目的', '研究方法与思路', '难点与创新思路', '论文进度及安排', '论文结论与展望')
      $menuShapes = @($items | Where-Object { $oldMenu -contains $_.Text } | Sort-Object Top, Left)
      for ($m = 0; $m -lt [Math]::Min($menuShapes.Count, $entry.body.Count); $m++) {
        Mark $menuShapes[$m] @($entry.body[$m])
      }
    } else {
      $navShapes = @($items | Where-Object { $_.Top -lt 95 -and $_.Left -gt 330 -and $_.Width -gt 45 } | Sort-Object Left)
      for ($n = 0; $n -lt [Math]::Min($navShapes.Count, $nav.Count); $n++) {
        Mark $navShapes[$n] @($nav[$n])
      }

      $titleCandidates = @($items | Where-Object { $_.Left -lt 310 -and $_.Top -lt 100 -and $_.Width -gt 90 } | Sort-Object Top, Left)
      if ($titleCandidates.Count -eq 0) {
        $titleCandidates = @($items | Sort-Object Area -Descending | Select-Object -First 1)
      }
      $titleItem = $titleCandidates[0]
      if ($titleItem) { Mark $titleItem @($entry.title) }

      $bodyCandidates = @($items | Where-Object { $_.Top -gt 105 -and $_.Shape.Id -ne $titleItem.Shape.Id -and $_.Area -gt 1500 } | Sort-Object Area -Descending)
      if ($bodyCandidates.Count -gt 0) {
        Mark $bodyCandidates[0] $entry.body
      } elseif ($titleItem) {
        Mark $titleItem @($entry.title + $entry.body)
      }
    }

    foreach ($item in $items) {
      if ($assigned.Contains([int]$item.Shape.Id)) { continue }
      if ($item.Text -like 'Page*') {
        Set-ShapeText $item.Shape @(('Page ' + $i))
      } elseif ($item.Text -match '^\d{1,2}$') {
        continue
      } else {
        Clear-ShapeText $item.Shape
      }
    }
  }

  $presentation.Save()
  $presentation.Close()
  $ppt.Quit()
  Write-Output 'UPDATED=$pptPath'
  Write-Output 'BACKUP=$backupPath'
} finally {
  if ($presentation -ne $null) {
    try { [System.Runtime.InteropServices.Marshal]::ReleaseComObject($presentation) | Out-Null } catch {}
  }
  if ($ppt -ne $null) {
    try { [System.Runtime.InteropServices.Marshal]::ReleaseComObject($ppt) | Out-Null } catch {}
  }
}
